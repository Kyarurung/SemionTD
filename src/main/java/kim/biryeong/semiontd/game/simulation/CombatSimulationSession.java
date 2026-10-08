package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.LongConsumer;
import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.entity.simulation.WorkerPhysics;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.ArenaCombatClock;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.mixin.accessor.CombatSimulationServerLevelAccessor;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitSimulation;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitWorld;
import kim.biryeong.semiontd.tower.legion.IllusionCloneSpawnQueue;
import kim.biryeong.semiontd.util.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;

public final class CombatSimulationSession implements CombatSimulationRuntime.Owner, AutoCloseable {
    private final MinecraftServer server;
    private final SemionGame game;
    private final int round;
    private final List<ServerLevel> worlds;
    private final Map<ServerLevel, Long> clocks = new IdentityHashMap<>();
    private final Map<ServerLevel, Long> completedClocks = new IdentityHashMap<>();
    private final Map<ServerLevel, EngineerCircuitWorld> circuits = new LinkedHashMap<>();
    private final Map<ServerLevel, EngineerCircuitSimulation.Snapshot> completedCircuits = new IdentityHashMap<>();
    private final Map<Entity, CombatSimulationRuntime.EntityView> views = new IdentityHashMap<>();
    private final Map<Entity, CombatSimulationRuntime.EntityView> completedViews = new IdentityHashMap<>();
    private final Map<Entity, Runnable> animations = new IdentityHashMap<>();
    private final Map<Entity, Runnable> completedAnimations = new IdentityHashMap<>();
    private final Coordinator<Entity, Work, WorkResult> coordinator;
    private long revision;
    private long logicalTick;
    private boolean publishing;
    private boolean closed;
    private LongConsumer stepObserver = ignored -> {};

    public CombatSimulationSession(MinecraftServer server, SemionGame game) {
        this.server = Objects.requireNonNull(server, "server");
        this.game = Objects.requireNonNull(game, "game");
        requireOwner();
        if (game.phase() != RoundPhase.LANE_WAVE || game.isSandboxMode() || game.isTutorialMode()) {
            throw new IllegalArgumentException("Combat sessions require an active normal wave.");
        }
        round = game.currentRound();
        logicalTick = game.currentTick();
        List<ServerLevel> arenaWorlds = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            if (game.arena().containsWorld(world)) {
                arenaWorlds.add(world);
                long time = CombatSimulationRuntime.nativeGameTime(world);
                clocks.put(world, time);
                completedClocks.put(world, time);
            }
        }
        if (arenaWorlds.isEmpty()) {
            throw new IllegalArgumentException("Combat sessions require loaded arena worlds.");
        }
        worlds = List.copyOf(arenaWorlds);
        coordinator = new Coordinator<>(new NativeBridge(), CombatSimulationSession::calculate, server::execute);
        try {
            for (ServerLevel world : worlds) {
                List<PlayerLane> lanes = game.teams().values().stream()
                        .flatMap(team -> team.laneGroup().lanes().stream())
                        .filter(lane -> lane.arenaWorld() == world).toList();
                EngineerCircuitWorld circuit = new EngineerCircuitWorld(world, lanes);
                circuits.put(world, circuit);
                completedCircuits.put(world, circuit.snapshot());
                CombatSimulationRuntime.register(world, this);
            }
            captureCompletedViews();
        } catch (RuntimeException | Error failure) {
            coordinator.close();
            throw failure;
        }
    }

    public void beginFrame(int logicalSteps) {
        requireOwner();
        coordinator.beginFrame(logicalSteps);
    }

    public void endFrame() {
        requireOwner();
        coordinator.endFrame();
    }

    public long logicalTickCount() {
        requireOwner();
        return coordinator.completedSteps();
    }

    public boolean idle() {
        requireOwner();
        return coordinator.idle();
    }

    public boolean isClosed() {
        requireOwner();
        return closed;
    }

    public void setStepObserver(LongConsumer observer) {
        requireOwner();
        stepObserver = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public void close() {
        requireOwner();
        coordinator.close();
    }

    @Override
    public boolean controls(Entity entity) {
        return !closed && worlds.contains(entity.level()) && EntitySimulationBridge.supports(entity);
    }

    @Override
    public int entityTick(Entity entity) {
        if (publishing) {
            CombatSimulationRuntime.EntityView completed = completedViews.get(entity);
            if (completed != null) {
                return completed.age();
            }
        }
        CombatSimulationRuntime.EntityView view = view(entity);
        return view == null ? entity.tickCount : view.age();
    }

    @Override
    public long gameTime(ServerLevel world) {
        Long time = (!publishing && CombatSimulationRuntime.active(this) ? clocks : completedClocks).get(world);
        return time == null ? CombatSimulationRuntime.nativeGameTime(world) : time;
    }

    @Override
    public CombatSimulationRuntime.EntityView view(Entity entity) {
        return controls(entity) && ((ServerLevel) entity.level()).getEntity(entity.getId()) == entity
                ? views.computeIfAbsent(entity, CombatSimulationRuntime.EntityView::capture) : null;
    }

    @Override
    public void changed(Entity entity) {
        requireOwner();
        if (publishing || !controls(entity)) {
            return;
        }
        revision++;
        CombatSimulationRuntime.EntityView view = view(entity);
        if (view == null) {
            return;
        }
        view.changed();
        if (!CombatSimulationRuntime.stepping(entity)) {
            coordinator.fail(new IllegalStateException("Owned combat mutation must be deferred through the input boundary."));
        }
    }

    @Override
    public void animate(Entity entity, SemionAnimationState animation, Runnable presentation) {
        requireOwner();
        animations.put(entity, Objects.requireNonNull(presentation, "presentation"));
    }

    @Override
    public void input(Runnable input) {
        requireOwner();
        coordinator.input(input);
    }

    @Override
    public Iterable<Entity> entities(ServerLevel world) {
        List<Entity> snapshot = new ArrayList<>();
        world.getAllEntities().forEach(snapshot::add);
        return List.copyOf(snapshot);
    }

    private void captureCompletedViews() {
        completedViews.clear();
        for (ServerLevel world : worlds) {
            for (Entity entity : entities(world)) {
                CombatSimulationRuntime.EntityView view = view(entity);
                if (view != null && !entity.isRemoved()) {
                    completedViews.put(entity, copy(view));
                }
            }
        }
    }

    private void publishCompleted() {
        publishing = true;
        try {
            for (var entry : completedViews.entrySet()) {
                if (!entry.getKey().isRemoved()) {
                    entry.getValue().publish(entry.getKey());
                    EntitySimulationBridge.present(entry.getKey());
                }
            }
            for (var entry : circuits.entrySet()) {
                EngineerCircuitSimulation.Snapshot snapshot = completedCircuits.get(entry.getKey());
                if (snapshot != null) {
                    entry.getValue().publish(snapshot);
                }
            }
            for (var entry : completedAnimations.entrySet()) {
                if (!entry.getKey().isRemoved()) {
                    entry.getValue().run();
                }
            }
            completedAnimations.clear();
        } finally {
            publishing = false;
        }
    }

    private static CombatSimulationRuntime.EntityView copy(CombatSimulationRuntime.EntityView view) {
        return new CombatSimulationRuntime.EntityView(view.position(), view.box(), view.velocity(), view.onGround(), view.age());
    }

    private void requireOwner() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Combat session access requires the server thread.");
        }
    }

    private static WorkResult calculate(Work work) {
        return switch (work) {
            case PhysicsWork physics -> new PhysicsResult(WorkerPhysics.advance(physics.input()));
            case CircuitWork circuit -> new CircuitResult(EngineerCircuitWorld.calculate(circuit.input()));
        };
    }

    private sealed interface Work permits PhysicsWork, CircuitWork {
    }

    private record PhysicsWork(WorkerPhysics.Input input) implements Work {
    }

    private record CircuitWork(EngineerCircuitWorld.Request input) implements Work {
    }

    private sealed interface WorkResult permits PhysicsResult, CircuitResult {
    }

    private record PhysicsResult(WorkerPhysics.Result output) implements WorkResult {
    }

    private record CircuitResult(EngineerCircuitWorld.Result output) implements WorkResult {
    }

    private final class NativeBridge implements Bridge<Entity, Work, WorkResult> {
        @Override
        public boolean valid() {
            return !closed && game.phase() == RoundPhase.LANE_WAVE && game.currentRound() == round
                    && server.tickRateManager().runsNormally()
                    && worlds.stream().allMatch(world -> server.getLevel(world.dimension()) == world
                            && world.tickRateManager().runsNormally());
        }

        @Override
        public long revision() { return revision; }

        @Override
        public long logicalTick() { return logicalTick; }

        @Override
        public int round() { return round; }

        @Override
        public List<Work> beginStep() {
            logicalTick++;
            clocks.replaceAll((world, time) -> Math.addExact(time, 1));
            List<Work> requests = new ArrayList<>();
            for (var entry : circuits.entrySet()) {
                requests.add(new CircuitWork(entry.getValue().request(clocks.get(entry.getKey()), List.of())));
            }
            return List.copyOf(requests);
        }

        @Override
        public void acceptCircuit(int index, WorkResult result) {
            EngineerCircuitWorld circuit = new ArrayList<>(circuits.values()).get(index);
            if (!(result instanceof CircuitResult output) || !circuit.accept(output.output())) {
                throw new IllegalStateException("Circuit state changed before its logical step could be committed.");
            }
        }

        @Override
        public List<Entity> actors() {
            List<Entity> actors = new ArrayList<>();
            for (ServerLevel world : worlds) {
                List<Entity> nativeOrder = new ArrayList<>();
                ((CombatSimulationServerLevelAccessor) world).semiontd$entityTickList().forEach(nativeOrder::add);
                for (Entity entity : nativeOrder) {
                    if (controls(entity)) {
                        actors.add(entity);
                    }
                }
            }
            return List.copyOf(actors);
        }

        @Override
        public boolean available(Entity actor) {
            CombatSimulationRuntime.EntityView view = view(actor);
            return actor instanceof Mob mob && !mob.isRemoved() && !mob.isPassenger() && controls(actor)
                    && view != null && ((ServerLevel) actor.level()).areEntitiesActuallyLoadedAndTicking(
                            new ChunkPos(Mth.floor(view.position().x) >> 4, Mth.floor(view.position().z) >> 4))
                    && !server.tickRateManager().isEntityFrozen(actor);
        }

        @Override
        public long actorRevision(Entity actor) { return view(actor).revision(); }

        @Override
        public boolean commitAvailable(Entity actor) { return available(actor) && ((Mob) actor).isAlive(); }

        @Override
        public Work prepare(Entity actor) {
            Work[] result = new Work[1];
            CombatSimulationRuntime.run(CombatSimulationSession.this, () -> {
                CombatSimulationRuntime.EntityView view = view(actor);
                view.age(Math.addExact(view.age(), 1));
                if (((Mob) actor).isAlive()) {
                    result[0] = new PhysicsWork(EntitySimulationBridge.prepare((Mob) actor));
                } else {
                    EntitySimulationBridge.ambient((Mob) actor);
                }
            });
            return result[0];
        }

        @Override
        public void apply(Entity actor, Work input, WorkResult result) {
            if (!(input instanceof PhysicsWork physics) || !(result instanceof PhysicsResult output)) {
                throw new IllegalStateException("Invalid combat movement completion.");
            }
            CombatSimulationRuntime.run(CombatSimulationSession.this, () -> {
                EntitySimulationBridge.apply((Mob) actor, physics.input(), output.output());
                EntitySimulationBridge.finish((Mob) actor);
            });
        }

        @Override
        public void abort(Entity actor) { EntitySimulationBridge.abort((Mob) actor); }

        @Override
        public void runInput(Runnable input) {
            CombatSimulationRuntime.run(CombatSimulationSession.this, () -> {
                revision++;
                input.run();
            });
        }

        @Override
        public void completeStep() {
            CombatSimulationRuntime.run(CombatSimulationSession.this, () -> {
                IllusionCloneSpawnQueue.tick(game.arena());
                game.tick(server);
                for (ServerLevel world : worlds) {
                    Scheduler.INSTANCE.runWorldTasks(world);
                }
                captureCompletedViews();
                completedAnimations.putAll(animations);
                animations.clear();
                completedClocks.putAll(clocks);
                circuits.forEach((world, circuit) -> completedCircuits.put(world, circuit.snapshot()));
                stepObserver.accept(coordinator.completedSteps() + 1);
            });
        }

        @Override
        public void present() { publishCompleted(); }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            publishCompleted();
            publishing = true;
            try {
                closed = true;
                CombatSimulationRuntime.unregister(CombatSimulationSession.this);
                for (var entry : completedClocks.entrySet()) {
                    ArenaCombatClock.adopt(entry.getKey(), entry.getValue());
                }
                for (var entry : completedViews.entrySet()) {
                    entry.getKey().tickCount = entry.getValue().age();
                }
                circuits.forEach((world, circuit) -> circuit.close(completedCircuits.get(world)));
                views.clear();
                animations.clear();
            } finally {
                publishing = false;
            }
        }
    }

    interface Bridge<A, I, O> {
        boolean valid();
        long revision();
        long logicalTick();
        int round();
        List<I> beginStep();
        void acceptCircuit(int index, O result);
        List<A> actors();
        boolean available(A actor);
        default boolean commitAvailable(A actor) { return available(actor); }
        long actorRevision(A actor);
        I prepare(A actor);
        void apply(A actor, I input, O result);
        void abort(A actor);
        void runInput(Runnable input);
        void completeStep();
        void present();
        void close();
    }

    static final class Coordinator<A, I, O> implements AutoCloseable {
        private static final int MAX_STEPS = 5;
        private static final int MAX_INPUTS = 256;
        private final Thread owner = Thread.currentThread();
        private final Bridge<A, I, O> bridge;
        private final CombatSimulationExecutor<I, O> executor;
        private final Deque<Runnable> inputs = new ArrayDeque<>();
        private List<I> circuits = List.of();
        private List<A> actors = List.of();
        private int circuitIndex;
        private int actorIndex;
        private int budget;
        private boolean active;
        private boolean closed;
        private boolean closeRequested;
        private boolean driving;
        private long inputSequence;
        private long completedSteps;
        private A pendingActor;
        private I pendingInput;
        private long pendingActorRevision;
        private CombatSimulationToken pendingToken;

        Coordinator(Bridge<A, I, O> bridge, Function<I, O> calculate, Executor dispatcher) {
            this.bridge = Objects.requireNonNull(bridge);
            executor = new CombatSimulationExecutor<>(calculate, dispatcher, this::ready);
        }

        void beginFrame(int steps) {
            requireOwner();
            checkNotification();
            if (steps < 1 || steps > MAX_STEPS) {
                throw new IllegalArgumentException("Combat frame budget must be within 1..5.");
            }
            if (!closed && !closeRequested && budget <= MAX_STEPS - steps) {
                budget += steps;
            }
            drive();
        }

        void endFrame() {
            requireOwner();
            checkNotification();
            if (!closed) {
                bridge.present();
            }
        }

        void input(Runnable input) {
            requireOwner();
            checkNotification();
            Objects.requireNonNull(input);
            if (closed || closeRequested) {
                throw new IllegalStateException("Combat session is closed.");
            }
            if (inputs.size() >= MAX_INPUTS) {
                throw new RejectedExecutionException("Combat input boundary is full.");
            }
            inputs.addLast(input);
            drive();
        }

        boolean stepActive() { return active; }

        int pendingSteps() { return budget; }

        long completedSteps() { return completedSteps; }

        boolean idle() { return !active && pendingToken == null && budget == 0 && inputs.isEmpty(); }

        boolean isClosed() { return closed; }

        private void drive() {
            if (driving || closed || pendingToken != null) {
                return;
            }
            driving = true;
            try {
                while (!closed && pendingToken == null) {
                    if (!bridge.valid()) {
                        closeNow();
                        return;
                    }
                    if (!active) {
                        while (!inputs.isEmpty()) {
                            bridge.runInput(inputs.removeFirst());
                            inputSequence++;
                            if (!bridge.valid()) {
                                closeNow();
                                return;
                            }
                        }
                        if (budget == 0) {
                            return;
                        }
                        active = true;
                        circuitIndex = 0;
                        actorIndex = 0;
                        actors = List.of();
                        circuits = List.copyOf(bridge.beginStep());
                    }
                    if (circuitIndex < circuits.size()) {
                        submit(circuits.get(circuitIndex));
                        return;
                    }
                    if (actors.isEmpty() && actorIndex == 0) {
                        actors = List.copyOf(bridge.actors());
                    }
                    while (actorIndex < actors.size() && !bridge.available(actors.get(actorIndex))) {
                        actorIndex++;
                    }
                    if (actorIndex < actors.size()) {
                        pendingActor = actors.get(actorIndex);
                        I input = bridge.prepare(pendingActor);
                        if (!bridge.valid()) {
                            closeNow();
                            return;
                        }
                        if (input == null) {
                            pendingActor = null;
                            actorIndex++;
                            continue;
                        }
                        pendingActorRevision = bridge.actorRevision(pendingActor);
                        submit(input);
                        return;
                    }
                    bridge.completeStep();
                    completedSteps++;
                    budget--;
                    active = false;
                    if (closeRequested) {
                        closeNow();
                        return;
                    }
                }
            } catch (RuntimeException | Error failure) {
                closeNow();
                throw failure;
            } finally {
                driving = false;
            }
        }

        private void submit(I input) {
            pendingInput = input;
            pendingToken = new CombatSimulationToken(executor.generation(), bridge.revision(),
                    bridge.logicalTick(), bridge.round(), inputSequence);
            if (!executor.offer(pendingToken, input)) {
                throw new IllegalStateException("Combat worker rejected its single cooperative request.");
            }
        }

        private void ready() {
            requireOwner();
            if (closed) {
                return;
            }
            var completion = executor.poll();
            if (completion.isEmpty()) {
                return;
            }
            var result = completion.orElseThrow();
            if (!bridge.valid()) {
                closeNow();
                return;
            }
            if (!result.token().equals(pendingToken) || pendingToken.revision() != bridge.revision()
                    || pendingToken.logicalTick() != bridge.logicalTick() || pendingToken.round() != bridge.round()
                    || pendingToken.inputSequence() != inputSequence) {
                fail(new IllegalStateException("Combat session changed before its worker result could be applied."));
            }
            if (result.error() != null) {
                fail(new IllegalStateException("Combat simulation calculation failed.", result.error()));
            }
            try {
                if (pendingActor == null) {
                    bridge.acceptCircuit(circuitIndex++, result.output());
                } else if (!bridge.commitAvailable(pendingActor)) {
                    bridge.abort(pendingActor);
                    actorIndex++;
                } else {
                    if (bridge.actorRevision(pendingActor) != pendingActorRevision) {
                        fail(new IllegalStateException("Prepared combat actor changed before its worker result could be applied."));
                    }
                    bridge.apply(pendingActor, pendingInput, result.output());
                    actorIndex++;
                }
                pendingActor = null;
                pendingInput = null;
                pendingToken = null;
                drive();
            } catch (RuntimeException | Error failure) {
                closeNow();
                throw failure;
            }
        }

        void fail(RuntimeException failure) {
            closeNow();
            throw failure;
        }

        private void checkNotification() {
            executor.notificationFailure().ifPresent(failure -> fail(
                    new IllegalStateException("Combat completion could not be dispatched to its owner.", failure)));
        }

        @Override
        public void close() {
            requireOwner();
            if (!closed && active && bridge.valid()) {
                closeRequested = true;
                budget = 1;
                return;
            }
            closeNow();
        }

        private void closeNow() {
            if (closed) {
                return;
            }
            closed = true;
            executor.close();
            try {
                if (pendingActor != null) {
                    bridge.abort(pendingActor);
                }
            } finally {
                inputs.clear();
                pendingActor = null;
                pendingInput = null;
                pendingToken = null;
                budget = 0;
                active = false;
                bridge.close();
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Combat coordination requires its owner thread.");
            }
        }
    }
}
