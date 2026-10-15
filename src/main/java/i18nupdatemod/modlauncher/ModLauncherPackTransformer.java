package i18nupdatemod.modlauncher;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import i18nupdatemod.core.PackSelectionTransformer;
import i18nupdatemod.core.RuntimePackActivation;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;

/**
 * Adapts the legacy generic transformer contract and ModLauncher 11's explicit
 * target type without linking newer API classes on Java 8 loaders.
 */
public final class ModLauncherPackTransformer implements ITransformer<ClassNode> {
    private static final Set<Target> TARGETS = Collections.singleton(
            Target.targetClass("net.minecraft.client.Options"));
    private static final PackSelectionTransformer TRANSFORMER = new PackSelectionTransformer();

    public static ITransformer<ClassNode> create() {
        ModLauncherPackTransformer delegate = new ModLauncherPackTransformer();
        final Method targetTypeMethod;
        try {
            targetTypeMethod = ITransformer.class.getMethod("getTargetType");
        } catch (NoSuchMethodException legacyApi) {
            // ModLauncher 8–10 infer ClassNode from this concrete generic interface.
            return delegate;
        }
        final Object classTargetType;
        try {
            classTargetType = targetTypeMethod.getReturnType().getField("CLASS").get(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to resolve ModLauncher class target type", failure);
        }
        // The runtime interface supplies the exact return descriptor introduced in 11.
        @SuppressWarnings("unchecked")
        ITransformer<ClassNode> adapter = (ITransformer<ClassNode>) Proxy.newProxyInstance(
                ITransformer.class.getClassLoader(), new Class<?>[]{ITransformer.class},
                (proxy, method, arguments) -> {
                    if (method.equals(targetTypeMethod)) {
                        return classTargetType;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        if ("equals".equals(method.getName())) return proxy == arguments[0];
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        return adapter;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (RuntimePackActivation.isEnabled()) {
            TRANSFORMER.transform(input);
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        return TARGETS;
    }

    @Override
    public String[] labels() {
        return new String[]{"i18nupdatemod.pack_selection"};
    }
}
