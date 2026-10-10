import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import me.lucko.spark.proto.SparkSamplerProtos;

public final class ProfileInspection {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.setOut(new java.io.PrintStream(Files.newOutputStream(Path.of(args[1])), true,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        if (args[0].endsWith(".sparkprofile")) {
            inspectSpark(Path.of(args[0]));
        } else {
            inspectJfr(Path.of(args[0]));
        }
    }

    private static void inspectSpark(Path path) throws Exception {
        var data = SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(path));
        for (var thread : data.getThreadsList()) {
            double total = thread.getTimesList().stream().mapToDouble(Double::doubleValue).sum();
            System.out.println("thread\t" + thread.getName() + "\t" + total + "\t" + thread.getChildrenCount());
            var nodes = thread.getChildrenList();
            Map<String, Double> inclusive = new HashMap<>();
            Map<String, Double> self = new HashMap<>();
            for (var node : nodes) {
                double elapsed = node.getTimesList().stream().mapToDouble(Double::doubleValue).sum();
                double children = 0;
                for (int index : node.getChildrenRefsList()) {
                    if (index < 0 || index >= nodes.size()) throw new IllegalStateException("Invalid Spark node reference");
                    children += nodes.get(index).getTimesList().stream().mapToDouble(Double::doubleValue).sum();
                }
                String name = node.getClassName() + "." + node.getMethodName();
                if (name.startsWith("kim.biryeong.semiontd.")) {
                    inclusive.merge(name, elapsed, Double::sum);
                    self.merge(name, elapsed - children, Double::sum);
                }
            }
            inclusive.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(80)
                    .forEach(entry -> System.out.println("spark\t" + thread.getName() + "\t" + entry.getKey()
                            + "\t" + entry.getValue() + "\t" + self.get(entry.getKey())));
        }
    }

    private static void inspectJfr(Path path) throws Exception {
        Map<String, Long> counts = new HashMap<>();
        Map<String, Long> inlining = new HashMap<>();
        Map<String, Long> samples = new HashMap<>();
        Map<String, Long> allocation = new HashMap<>();
        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                counts.merge(type, 1L, Long::sum);
                if (type.equals("jdk.CompilerInlining")) {
                    RecordedObject callee = event.getValue("callee");
                    String name = callee.getString("type") + "." + callee.getString("name");
                    if (name.startsWith("kim/biryeong/semiontd/")) {
                        String decision = name + "\t" + event.getBoolean("succeeded") + "\t" + event.getString("message");
                        inlining.merge(decision, 1L, Long::sum);
                    }
                }
                if (type.equals("jdk.ExecutionSample") || type.equals("jdk.ObjectAllocationSample")) {
                    if (event.getStackTrace() == null) continue;
                    for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                        RecordedMethod method = frame.getMethod();
                        String name = method.getType().getName() + "." + method.getName();
                        if (name.startsWith("kim.biryeong.semiontd.")) {
                            if (type.equals("jdk.ExecutionSample")) samples.merge(name, 1L, Long::sum);
                            else allocation.merge(name, event.getLong("weight"), Long::sum);
                            break;
                        }
                    }
                }
            }
        }
        counts.forEach((name, count) -> System.out.println("event\t" + name + "\t" + count));
        inlining.forEach((decision, count) -> System.out.println("inline\t" + decision + "\t" + count));
        samples.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(60)
                .forEach(entry -> System.out.println("sample\t" + entry.getKey() + "\t" + entry.getValue()));
        allocation.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(60)
                .forEach(entry -> System.out.println("allocation-weight\t" + entry.getKey() + "\t" + entry.getValue()));
    }
}
