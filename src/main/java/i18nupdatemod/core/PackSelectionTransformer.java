package i18nupdatemod.core;

import i18nupdatemod.util.Log;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds runtime resource-pack selection callbacks to Minecraft's Options class.
 *
 * <p>Game method names are deliberately not part of the binding. Mappings and
 * obfuscation change those names between loaders and versions, while the
 * repository, pack, and collection descriptors remain stable. This transformer
 * binds both lifecycle methods from their bytecode shape and passes the discovered
 * names to the runtime activation code.</p>
 */
public final class PackSelectionTransformer {
    private static final String OPTIONS = "net/minecraft/client/Options";
    private static final String PACK_REPOSITORY = "net/minecraft/server/packs/repository/PackRepository";
    private static final String PACK = "net/minecraft/server/packs/repository/Pack";
    private static final String COLLECTION = "Ljava/util/Collection;";
    private static final String LIST = "Ljava/util/List;";
    private static final String STRING = "Ljava/lang/String;";
    private static final String LIST_FIELD = "Ljava/util/List;";

    private static final String CALLBACK_OWNER = "i18nupdatemod/core/RuntimePackActivation";
    private static final String BEFORE_NAME = "beforeSelection";
    private static final String BEFORE_DESC = "(Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String AFTER_NAME = "afterSelection";
    private static final String AFTER_DESC = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";

    private static final String REPOSITORY_ARGUMENT = "(L" + PACK_REPOSITORY + ";)V";
    private static final String SET_SELECTED_DESC = "(" + COLLECTION + ")V";

    /**
     * Transforms exactly one class: the client Options class. A false result means
     * either that the class is not Options or that its current bytecode did not offer
     * an unambiguous binding.
     */
    public boolean transform(ClassNode classNode) {
        if (classNode == null || !OPTIONS.equals(classNode.name)) {
            return false;
        }

        Binding binding = bind(classNode);
        if (binding == null) {
            Log.warning("Unable to bind runtime resource-pack selection; enable the generated pack manually.");
            return false;
        }

        boolean changed = false;
        if (!containsCallback(binding.selectionMethod, BEFORE_NAME, BEFORE_DESC)) {
            binding.selectionMethod.instructions.insert(newBeforeInstructions(binding.selectedFieldName));
            changed = true;
        }
        if (!containsCallback(binding.selectionMethod, AFTER_NAME, AFTER_DESC)) {
            InsnList after = newAfterInstructions(binding);
            for (AbstractInsnNode instruction = binding.selectionMethod.instructions.getLast();
                 instruction != null;
                 instruction = instruction.getPrevious()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    binding.selectionMethod.instructions.insertBefore(instruction, cloneInstructions(after));
                    changed = true;
                }
            }
        }

        if (changed) {
            // The largest injected sequence has six object references on the stack.
            // No locals, branches, or frames are introduced.
            binding.selectionMethod.maxStack = Math.max(binding.selectionMethod.maxStack, 6);
        }
        return changed;
    }

    private static Binding bind(ClassNode classNode) {
        MethodNode selection = null;
        String setSelectedName = null;
        MethodNode update = null;
        String selectedPacksName = null;
        String packIdName = null;
        String fixedPositionName = null;

        for (MethodNode method : classNode.methods) {
            if (!REPOSITORY_ARGUMENT.equals(method.desc)) {
                continue;
            }

            String setName = findSetSelectedName(method);
            if (setName != null) {
                if (selection != null) {
                    // An ambiguous shape is safer to leave untouched than to hook
                    // the wrong lifecycle method.
                    return null;
                }
                selection = method;
                setSelectedName = setName;
                continue;
            }

            RepositorySelectionBinding repositoryBinding =
                    findRepositorySelectionBinding(method);
            if (repositoryBinding != null) {
                if (update != null) {
                    return null;
                }
                update = method;
                selectedPacksName = repositoryBinding.selectedPacksName;
                packIdName = repositoryBinding.packIdName;
                fixedPositionName = repositoryBinding.fixedPositionName;
            }
        }

        if (selection == null || update == null || setSelectedName == null
                || selectedPacksName == null || packIdName == null
                || fixedPositionName == null) {
            return null;
        }

        String selectedFieldName = findSelectedFieldName(update, classNode.name);
        if (selectedFieldName == null) {
            return null;
        }

        return new Binding(selection, selectedFieldName, selectedPacksName, packIdName,
                fixedPositionName, setSelectedName);
    }

    private static String findSetSelectedName(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (PACK_REPOSITORY.equals(invocation.owner)
                    && SET_SELECTED_DESC.equals(invocation.desc)
                    && invocation.getOpcode() != Opcodes.INVOKESTATIC) {
                return invocation.name;
            }
        }
        return null;
    }

    private static RepositorySelectionBinding findRepositorySelectionBinding(
            MethodNode method) {
        String selectedPacksName = null;
        String packIdName = null;
        String fixedPositionName = null;

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (PACK_REPOSITORY.equals(invocation.owner)
                        && isPackCollectionGetter(invocation.desc)
                        && invocation.getOpcode() != Opcodes.INVOKESTATIC) {
                    selectedPacksName = invocation.name;
                }
                if (PACK.equals(invocation.owner)
                        && ("()" + STRING).equals(invocation.desc)
                        && invocation.getOpcode() != Opcodes.INVOKESTATIC) {
                    packIdName = invocation.name;
                }
                if (PACK.equals(invocation.owner)
                        && "()Z".equals(invocation.desc)
                        && invocation.getOpcode() != Opcodes.INVOKESTATIC
                        && fixedPositionName == null) {
                    // Vanilla's update loop tests Pack.isFixedPosition before the
                    // NeoForge-only isHidden test. Binding the first Pack boolean
                    // accessor avoids hardcoding either mapped method name.
                    fixedPositionName = invocation.name;
                }
            } else if (instruction instanceof InvokeDynamicInsnNode) {
                // A compiler is allowed to express Pack::getId or a boolean pack
                // accessor through a method reference. Keep the same structural
                // binding in that case.
                InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) instruction;
                for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle) {
                        Handle handle = (Handle) argument;
                        if (PACK.equals(handle.getOwner())
                                && ("()" + STRING).equals(handle.getDesc())) {
                            packIdName = handle.getName();
                        }
                        if (PACK.equals(handle.getOwner())
                                && "()Z".equals(handle.getDesc())
                                && fixedPositionName == null) {
                            fixedPositionName = handle.getName();
                        }
                    }
                }
            }
        }

        if (selectedPacksName == null || packIdName == null
                || fixedPositionName == null) {
            return null;
        }
        return new RepositorySelectionBinding(selectedPacksName, packIdName,
                fixedPositionName);
    }

    private static boolean isPackCollectionGetter(String descriptor) {
        return ("()" + COLLECTION).equals(descriptor) || ("()" + LIST).equals(descriptor);
    }

    /**
     * The first list cleared while rebuilding Options' selections is resourcePacks;
     * incompatibleResourcePacks is cleared afterwards. Restricting the match to a
     * GETFIELD immediately consumed by List.clear avoids binding a different List
     * field used elsewhere in the same method.
     */
    private static String findSelectedFieldName(MethodNode update, String optionsOwner) {
        for (AbstractInsnNode instruction = update.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.INVOKEINTERFACE
                    && instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode clear = (MethodInsnNode) instruction;
            if (!"java/util/List".equals(clear.owner)
                    || !"clear".equals(clear.name)
                    || !"()V".equals(clear.desc)) {
                continue;
            }

            AbstractInsnNode previous = previousRealInstruction(instruction);
            if (!(previous instanceof FieldInsnNode)) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode) previous;
            if (field.getOpcode() == Opcodes.GETFIELD
                    && optionsOwner.equals(field.owner)
                    && LIST_FIELD.equals(field.desc)) {
                return field.name;
            }
        }
        return null;
    }

    private static AbstractInsnNode previousRealInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && (previous.getType() == AbstractInsnNode.LABEL
                || previous.getType() == AbstractInsnNode.LINE
                || previous.getType() == AbstractInsnNode.FRAME)) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static boolean containsCallback(MethodNode method, String name, String descriptor) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (CALLBACK_OWNER.equals(invocation.owner)
                        && name.equals(invocation.name)
                        && descriptor.equals(invocation.desc)
                        && invocation.getOpcode() == Opcodes.INVOKESTATIC) {
                    return true;
                }
            }
        }
        return false;
    }

    private static InsnList newBeforeInstructions(String selectedFieldName) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new LdcInsnNode(selectedFieldName));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CALLBACK_OWNER,
                BEFORE_NAME, BEFORE_DESC, false));
        return instructions;
    }

    private static InsnList newAfterInstructions(Binding binding) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new LdcInsnNode(binding.selectedPacksMethodName));
        instructions.add(new LdcInsnNode(binding.packIdMethodName));
        instructions.add(new LdcInsnNode(binding.fixedPositionMethodName));
        instructions.add(new LdcInsnNode(binding.setSelectedMethodName));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CALLBACK_OWNER,
                AFTER_NAME, AFTER_DESC, false));
        return instructions;
    }

    /**
     * InsnList nodes cannot be shared between multiple insertion points. Cloning is
     * intentionally limited to the node kinds emitted by newAfterInstructions.
     */
    private static InsnList cloneInstructions(InsnList source) {
        InsnList clone = new InsnList();
        for (AbstractInsnNode instruction = source.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            clone.add(instruction.clone(null));
        }
        return clone;
    }

    private static final class RepositorySelectionBinding {
        private final String selectedPacksName;
        private final String packIdName;
        private final String fixedPositionName;

        private RepositorySelectionBinding(String selectedPacksName, String packIdName,
                                           String fixedPositionName) {
            this.selectedPacksName = selectedPacksName;
            this.packIdName = packIdName;
            this.fixedPositionName = fixedPositionName;
        }
    }

    private static final class Binding {
        private final MethodNode selectionMethod;
        private final String selectedFieldName;
        private final String selectedPacksMethodName;
        private final String packIdMethodName;
        private final String fixedPositionMethodName;
        private final String setSelectedMethodName;

        private Binding(MethodNode selectionMethod, String selectedFieldName,
                        String selectedPacksMethodName, String packIdMethodName,
                        String fixedPositionMethodName, String setSelectedMethodName) {
            this.selectionMethod = selectionMethod;
            this.selectedFieldName = selectedFieldName;
            this.selectedPacksMethodName = selectedPacksMethodName;
            this.packIdMethodName = packIdMethodName;
            this.fixedPositionMethodName = fixedPositionMethodName;
            this.setSelectedMethodName = setSelectedMethodName;
        }
    }
}
