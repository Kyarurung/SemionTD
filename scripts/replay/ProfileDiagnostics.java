import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

public final class ProfileDiagnostics {
    private static final class ThreadProfile {
        long executionSamples;
        long nativeSamples;
        long allocationSamples;
        long allocationWeight;
        long parkEvents;
        long parkNanos;
        final Map<String, Long> executionLeaf = new LinkedHashMap<>();
        final Map<String, Long> executionInclusive = new LinkedHashMap<>();
        final Map<String, Long> allocationClasses = new LinkedHashMap<>();
        final Map<String, Long> allocationProjectFrames = new LinkedHashMap<>();
        final Map<String, Long> allocationProjectSamples = new LinkedHashMap<>();
        final Map<String, Long> parkStacks = new LinkedHashMap<>();

        Map<String, Object> output() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("execution_samples", executionSamples);
            result.put("native_method_samples", nativeSamples);
            result.put("allocation_samples", allocationSamples);
            result.put("sampled_allocation_weight_bytes", allocationWeight);
            result.put("park_events", parkEvents);
            result.put("park_duration_ns_clipped_to_window", parkNanos);
            result.put("execution_leaf", top(executionLeaf));
            result.put("execution_inclusive", top(executionInclusive));
            result.put("allocation_classes_weight_bytes", top(allocationClasses));
            result.put("allocation_project_frame_weight_bytes", top(allocationProjectFrames));
            result.put("allocation_project_frame_samples", top(allocationProjectSamples));
            result.put("park_stacks_duration_ns", top(parkStacks));
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        List<Object> results = new ArrayList<>();
        for (String argument : args) {
            Path path = Path.of(argument);
            RecordedEvent window = null;
            try (RecordingFile file = new RecordingFile(path)) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    if (event.getEventType().getName().equals("semiontd.BenchmarkWindow")) {
                        if (window != null) { throw new IllegalStateException("Multiple battle windows: " + path); }
                        window = event;
                    }
                }
            }
            if (window == null) { throw new IllegalStateException("Missing exact battle marker: " + path); }
            Instant begin = window.getStartTime();
            Instant end = window.getEndTime();
            Map<String, ThreadProfile> threads = new LinkedHashMap<>();
            try (RecordingFile file = new RecordingFile(path)) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    String type = event.getEventType().getName();
                    boolean park = type.equals("jdk.ThreadPark");
                    boolean execution = type.equals("jdk.ExecutionSample");
                    boolean nativeSample = type.equals("jdk.NativeMethodSample");
                    boolean allocation = type.equals("jdk.ObjectAllocationSample");
                    if (!park && !execution && !nativeSample && !allocation) { continue; }
                    if (park ? !event.getEndTime().isAfter(begin) || !event.getStartTime().isBefore(end)
                            : event.getStartTime().isBefore(begin) || event.getStartTime().isAfter(end)) { continue; }
                    RecordedThread thread = event.getThread(execution || nativeSample ? "sampledThread" : "eventThread");
                    if (thread == null) { continue; }
                    ThreadProfile profile = threads.computeIfAbsent(thread.getJavaName(), ignored -> new ThreadProfile());
                    List<String> frames = event.getStackTrace() == null ? List.of() : event.getStackTrace().getFrames().stream()
                            .map(frame -> frame.getMethod().getType().getName() + "." + frame.getMethod().getName()).toList();
                    if (execution) {
                        profile.executionSamples++;
                        add(profile.executionLeaf, frames.isEmpty() ? "UNKNOWN" : frames.get(0), 1);
                        for (String frame : new HashSet<>(frames)) { add(profile.executionInclusive, frame, 1); }
                    } else if (nativeSample) {
                        profile.nativeSamples++;
                    } else if (allocation) {
                        long weight = event.getLong("weight");
                        profile.allocationSamples++;
                        profile.allocationWeight += weight;
                        add(profile.allocationClasses, event.getClass("objectClass").getName(), weight);
                        String project = frames.stream().filter(frame -> frame.startsWith("kim.biryeong.semiontd."))
                                .findFirst().orElse("NO_PROJECT_FRAME");
                        add(profile.allocationProjectFrames, project, weight);
                        add(profile.allocationProjectSamples, project, 1);
                    } else {
                        Instant clippedBegin = event.getStartTime().isBefore(begin) ? begin : event.getStartTime();
                        Instant clippedEnd = event.getEndTime().isAfter(end) ? end : event.getEndTime();
                        long nanos = Duration.between(clippedBegin, clippedEnd).toNanos();
                        profile.parkEvents++;
                        profile.parkNanos += nanos;
                        String project = frames.stream().filter(frame -> frame.startsWith("kim.biryeong.semiontd."))
                                .findFirst().orElse(String.join(" <- ", frames.stream().limit(5).toList()));
                        add(profile.parkStacks, project, nanos);
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("recording", path.toString());
            result.put("repetition", window.getInt("repetition"));
            result.put("warmup", window.getBoolean("warmup"));
            result.put("logical_ticks", window.getLong("logicalTicks"));
            result.put("window_start", begin.toString());
            result.put("window_end", end.toString());
            result.put("window_duration_ns", Duration.between(begin, end).toNanos());
            Map<String, Object> output = new LinkedHashMap<>();
            threads.forEach((name, profile) -> output.put(name, profile.output()));
            result.put("threads", output);
            results.add(result);
        }
        System.out.println(json(results));
    }

    private static void add(Map<String, Long> values, String key, long amount) { values.merge(key, amount, Long::sum); }

    private static Map<String, Long> top(Map<String, Long> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(100)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) {
            return "{" + String.join(",", map.entrySet().stream().map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue())).toList()) + "}";
        }
        if (value instanceof List<?> list) { return "[" + String.join(",", list.stream().map(ProfileDiagnostics::json).toList()) + "]"; }
        if (value instanceof Number || value instanceof Boolean) { return value.toString(); }
        String text = value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + text + "\"";
    }
}
