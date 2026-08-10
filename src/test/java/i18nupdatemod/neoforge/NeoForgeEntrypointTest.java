package i18nupdatemod.neoforge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeEntrypointTest {
    @Test
    void declaresTheClientOnlyNeoForgeEntrypoint() throws Exception {
        AtomicBoolean foundModAnnotation = new AtomicBoolean();
        AtomicReference<String> modId = new AtomicReference<>();
        AtomicReference<String> dist = new AtomicReference<>();

        try (InputStream input = NeoForgeMod.class.getResourceAsStream("/i18nupdatemod/neoforge/NeoForgeMod.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!"Lnet/neoforged/fml/common/Mod;".equals(descriptor)) {
                        return null;
                    }
                    foundModAnnotation.set(visible);
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("value".equals(name)) {
                                modId.set((String) value);
                            }
                        }

                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            if (!"dist".equals(name)) {
                                return null;
                            }
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitEnum(String name, String descriptor, String value) {
                                    dist.set(value);
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(foundModAnnotation.get());
        assertEquals("i18nupdatemod", modId.get());
        assertEquals("CLIENT", dist.get());
    }

    /**
     * FML 11 (Minecraft 26.1+) exposes instance {@code getCurrent().getGameDir()/getVersionInfo()},
     * while FML 1~4 (Minecraft 1.20.1~1.21.x) exposes static {@code getGamePath()/versionInfo()}.
     * The same universal jar loads on both, so the entrypoint must not bind either shape directly.
     */
    @Test
    void resolvesBothFmlApiShapesWithoutHardBinding() throws Exception {
        Set<String> fmlMethods = new HashSet<>();
        try (InputStream input = NeoForgeMod.class.getResourceAsStream("/i18nupdatemod/neoforge/NeoForgeMod.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            if (owner.startsWith("net/neoforged/fml/loading/")) {
                                fmlMethods.add(owner + "." + name);
                            }
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String) {
                                fmlMethods.add("LDC:" + value);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        // No direct call may reach FMLLoader, or loading fails on whichever FML lacks that method.
        assertTrue(fmlMethods.stream().noneMatch(it -> it.startsWith("net/neoforged/fml/loading/")),
                "NeoForgeMod must not link FMLLoader directly, found: " + fmlMethods);
        // Both API shapes must be attempted reflectively.
        assertTrue(fmlMethods.contains("LDC:getCurrent()"));
        assertTrue(fmlMethods.contains("LDC:getGamePath()"));
        assertTrue(fmlMethods.contains("LDC:versionInfo()"));
        assertTrue(fmlMethods.contains("LDC:getVersionInfo()"));
    }
}
