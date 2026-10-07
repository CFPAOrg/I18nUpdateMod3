package i18nupdatemod.neoforgeloader;

import i18nupdatemod.core.PackSelectionTransformer;
import i18nupdatemod.core.RuntimePackActivation;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * NeoForge 10+ class-processor adapter. It intentionally keeps the shared
 * transformer free of FML classes so the same core logic can run through old
 * ModLauncher and the modern early-service SPI.
 */
public final class NeoForgePackProcessor implements ClassProcessor {
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final ProcessorName NAME = new ProcessorName("i18nupdatemod", "pack_selection");
    private static final PackSelectionTransformer TRANSFORMER = new PackSelectionTransformer();

    private static volatile Method selectionTypeMethod;
    private static volatile Class<?> selectionContextClass;

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        // Let NeoForge's own mixin/core processors finish first so this hook is
        // attached to the final Options selection method.
        Set<ProcessorName> predecessors = new HashSet<>();
        predecessors.add(ClassProcessorIds.COMPUTING_FRAMES);
        predecessors.add(ClassProcessorIds.MIXIN);
        return predecessors;
    }

    @Override
    public boolean handlesClass(ClassProcessor.SelectionContext context) {
        if (!RuntimePackActivation.isEnabled() || context == null) {
            return false;
        }
        try {
            Object typeValue = getSelectionTypeMethod(context).invoke(context);
            return typeValue instanceof Type
                    && OPTIONS.equals(((Type) typeValue).getInternalName());
        } catch (Exception ignored) {
            // SelectionContext is a Java record in FML10. Calling its accessor
            // reflectively keeps this class Java-8-linkable without a Record API.
            return false;
        }
    }

    @Override
    public ClassProcessor.ComputeFlags processClass(ClassProcessor.TransformationContext context) {
        if (!RuntimePackActivation.isEnabled() || context == null) {
            return ClassProcessor.ComputeFlags.NO_REWRITE;
        }
        ClassNode node = context.node();
        if (node == null || !TRANSFORMER.transform(node)) {
            return ClassProcessor.ComputeFlags.NO_REWRITE;
        }
        // Only maxStack changes; no locals, branches, or frames are introduced.
        return ClassProcessor.ComputeFlags.COMPUTE_MAXS;
    }

    private static Method getSelectionTypeMethod(Object context) throws NoSuchMethodException {
        Class<?> contextClass = context.getClass();
        Method method = selectionTypeMethod;
        if (method == null || selectionContextClass != contextClass) {
            synchronized (NeoForgePackProcessor.class) {
                method = selectionTypeMethod;
                if (method == null || selectionContextClass != contextClass) {
                    // Do not call context.type() directly: javac --release 8
                    // cannot resolve an accessor declared on a Java record.
                    method = ((Object) context).getClass().getMethod("type");
                    selectionContextClass = contextClass;
                    selectionTypeMethod = method;
                }
            }
        }
        return method;
    }
}
