package me.drex.fafpatch.impl.entity;

import eu.pb4.factorytools.api.virtualentity.emuvanilla.EntityModelTransforms;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.PolyModelInstance;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.EntityModel;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.ModelPart;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import me.drex.fafpatch.impl.entity.model.EntityModelHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.*;
import java.util.function.Function;

public class SimpleElementHolder<T extends Entity, X extends EntityModel<T>> extends ElementHolder {

    public static final Identifier MAIN_LAYER = FriendsAndFoesPatch.id("main_layer");
    static final Matrix4fStack STACK = new Matrix4fStack(64);
    private final Map<PolyModelInstance<X>, Map<ModelPart, ItemDisplayElement>> elements = new IdentityHashMap<>();
    protected final T entity;
    public final InteractionElement interaction;
    public final LeadAttachmentElement leadAttachment = new LeadAttachmentElement();

    final Map<Identifier, PolyModelInstance<X>> layerModels = new HashMap<>();
    final List<ConditionalLayer<?>> conditionalLayers = new ArrayList<>();
    private boolean hurt = false;

    private boolean noTick = true;

    public SimpleElementHolder(T entity) {
        this.entity = entity;
        var interaction = VirtualElement.InteractionHandler.redirect(entity);
        this.interaction = new InteractionElement(interaction);
        this.interaction.setSendPositionUpdates(false);
        this.leadAttachment.setOffset(new Vec3(0, entity.getBbHeight() / 2, 0));
        this.leadAttachment.setInteractionHandler(interaction);
        this.addElement(leadAttachment);
        this.addPassengerElement(this.interaction);
    }

    public <L> void addConditionalLayer(Function<T, L> stateSupplier, Identifier layer, Function<L, PolyModelInstance<X>> modelSupplier) {
        conditionalLayers.add(new ConditionalLayer<>(stateSupplier, layer, modelSupplier));
    }

    public void setMainModel(PolyModelInstance<X> main) {
        setLayer(MAIN_LAYER, main);
    }

    public PolyModelInstance<X> getMainModel() {
        return layerModels.get(MAIN_LAYER);
    }

    public void setLayer(Identifier id, PolyModelInstance<X> layer) {
        if (layerModels.get(id) == layer) {
            return;
        }
        replaceModel(layerModels.get(id), layer);
        if (layer != null) {
            layerModels.put(id, layer);
        } else {
            layerModels.remove(id);
        }
        if (!noTick) {
            this.tick();
        }
    }

    private void replaceModel(PolyModelInstance<X> oldModel, PolyModelInstance<X> newModel) {
        Map<ModelPart, ItemDisplayElement> oldElements;
        if (oldModel == null) {
            oldElements = new IdentityHashMap<>();
        } else {
            oldElements = new IdentityHashMap<>(this.elements.get(oldModel));
        }
        var newElements = new IdentityHashMap<ModelPart, ItemDisplayElement>();
        this.elements.remove(oldModel);

        if (newModel != null) {
            for (var part : newModel.model().allParts()) {
                var stack = newModel.modelParts().apply(part);
                if (stack != null) {
                    var element = oldElements.get(part);
                    if (element == null) {
                        element = EntityModelHelper.createItemDisplay(stack.get());
                    } else {
                        element.setItem(stack.get());
                        oldElements.remove(part);
                    }
                    EntityModelHelper.updateDisplayElement(element, this.entity);
                    newElements.put(part, element);
                    this.addElement(element);
                }
            }
            this.elements.put(newModel, newElements);
        }

        for (var old : oldElements.values()) {
            this.removeElement(old);
        }
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (noTick) {
            onTick();
        }
        return super.startWatching(player);
    }

    @Override
    protected void onTick() {
        noTick = false;
        this.interaction.setSize(entity.getBbWidth(), entity.getBbHeight());
        this.interaction.setCustomName(this.entity.getCustomName());
        this.interaction.setCustomNameVisible(this.entity.isCustomNameVisible());

        conditionalLayers.forEach(ConditionalLayer::tick);

        for (var layerModel : layerModels.values()) {
            renderModel(layerModel);
        }
        if (entity instanceof LivingEntity livingEntity) {
            this.hurt = livingEntity.hurtTime > 0 || livingEntity.deathTime > 0;
        }

        super.onTick();
    }

    private void renderModel(PolyModelInstance<X> model) {
        STACK.pushMatrix();
        // ensure the element doesn't clip into nearby blocks
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        STACK.translate(0.0F, -dimensions.height() / 2, 0.0F);
        if (entity instanceof LivingEntity livingEntity) {
            var hurt = livingEntity.hurtTime > 0 || livingEntity.deathTime > 0;
            if (this.hurt != hurt) {

                var map = hurt ? model.damagedModelParts() : model.modelParts();

                for (var entry : elements.get(model).entrySet()) {
                    var stack = map.apply(entry.getKey());
                    if (stack != null) {
                        entry.getValue().setItem(stack.get());
                    }
                }
            }
            EntityModelTransforms.livingEntityTransform(livingEntity, STACK, matrix4f -> matrix4f.scale(getEntityScale()));
        }

        model.model().setupAnim(this.entity);
        model.model().render(STACK, (part, matrix4f, hidden) -> updateElement(model, part, matrix4f, hidden));
        renderSpecialLayers(STACK);

        STACK.popMatrix();
    }

    protected void renderSpecialLayers(Matrix4fStack stack) {
    }

    public float getEntityScale() {
        return 1;
    }

    private void updateElement(PolyModelInstance<X> model, ModelPart part, Matrix4f matrix4f, boolean hidden) {
        var element = this.elements.get(model).get(part);
        if (element == null) {
            return;
        }

        List<VirtualElement> virtualElements = this.getElements();
        if (virtualElements.contains(element) && hidden) {
            this.removeElement(element);
        } else if (!virtualElements.contains(element) && !hidden) {
            this.addElement(element);
            element.setTeleportDuration(0);
        } else if (!hidden) {
            element.setTeleportDuration(EntityModelHelper.TELEPORT_DURATION);
        }

        if (!hidden) {
            element.setTransformation(matrix4f);
            EntityModelHelper.updateDisplayElement(element, this.entity);
            element.startInterpolationIfDirty();
        }
    }

    private class ConditionalLayer<S> {
        private final Function<T, S> stateSupplier;
        private final Identifier layer;
        private final Function<S, PolyModelInstance<X>> modelSupplier;
        S previous;

        private ConditionalLayer(Function<T, S> stateSupplier, Identifier layer, Function<S, PolyModelInstance<X>> modelSupplier) {
            this.stateSupplier = stateSupplier;
            this.layer = layer;
            this.modelSupplier = modelSupplier;
        }

        void tick() {
            S current = stateSupplier.apply(entity);
            if (!Objects.equals(current, previous)) {
                // update
                setLayer(layer, modelSupplier.apply(current));
            }

            previous = current;
        }

    }
}
