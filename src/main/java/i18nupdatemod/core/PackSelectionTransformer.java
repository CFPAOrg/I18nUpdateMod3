package i18nupdatemod.core;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private static final String BEFORE_DESC = "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
    private static final String AFTER_NAME = "afterSelection";
    private static final String AFTER_DESC = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
    private static final String FRAME_TOKEN = "java/lang/Object";

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
            RuntimePackActivation.onBindingFailure();
            return false;
        }

        boolean hasBefore = containsCallback(binding.selectionMethod, BEFORE_NAME, BEFORE_DESC);
        boolean hasAfter = containsCallback(binding.selectionMethod, AFTER_NAME, AFTER_DESC);
        if (hasBefore && hasAfter) {
            return false;
        }
        if (hasBefore || hasAfter) {
            // A partially transformed target class has no reliable way to
            // identify the token slot. Leave it unchanged and report the
            // unavailable hook through the explicit P4 fallback.
            RuntimePackActivation.onBindingFailure();
            return false;
        }

        final int tokenIndex;
        final FramePlan framePlan;
        try {
            tokenIndex = freshLocalIndex(binding.selectionMethod);
            if (tokenIndex < 0 || tokenIndex >= 65535) {
                throw new IllegalArgumentException("no JVM local slot for activation token");
            }
            framePlan = planFrames(classNode, binding.selectionMethod, tokenIndex);
        } catch (RuntimeException unsupported) {
            // Frame validation failed before any class mutation. This target
            // class cannot safely receive the hook, so use explicit fallback.
            RuntimePackActivation.onBindingFailure();
            return false;
        }

        // Frame validation is complete before any instruction or class metadata
        // is changed. This keeps fallback safe when a loader supplies a frame
        // shape that cannot be represented without resolving game classes.
        framePlan.apply();
        binding.selectionMethod.instructions.insert(
                newBeforeInstructions(binding.selectedFieldName, tokenIndex));
        InsnList after = newAfterInstructions(binding, tokenIndex);
        for (AbstractInsnNode instruction = binding.selectionMethod.instructions.getLast();
             instruction != null;
             instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                binding.selectionMethod.instructions.insertBefore(instruction,
                        cloneInstructions(after));
            }
        }

        // The after callback consumes seven object/string references. The
        // token occupies a genuinely fresh slot after every pre-existing local.
        binding.selectionMethod.maxStack = Math.max(binding.selectionMethod.maxStack, 7);
        binding.selectionMethod.maxLocals = tokenIndex + 1;
        return true;
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
                || fixedPositionName == null || !containsReturn(selection)
                || (selection.access & Opcodes.ACC_STATIC) != 0) {
            return null;
        }

        String selectedFieldName = findSelectedFieldName(update, classNode.name);
        if (selectedFieldName == null) {
            return null;
        }

        return new Binding(selection, selectedFieldName, selectedPacksName, packIdName,
                fixedPositionName, setSelectedName);
    }

    private static boolean containsReturn(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                return true;
            }
        }
        return false;
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

    /**
     * Computes a slot that is beyond every local the original instance method
     * could address. Its recorded maxLocals is authoritative when larger than
     * the instruction scan; the exact binding shape always starts at slots 0/1.
     */
    private static int freshLocalIndex(MethodNode method) {
        int limit = 2;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof VarInsnNode) {
                VarInsnNode variable = (VarInsnNode) instruction;
                int width = (variable.getOpcode() == Opcodes.LLOAD
                        || variable.getOpcode() == Opcodes.DLOAD
                        || variable.getOpcode() == Opcodes.LSTORE
                        || variable.getOpcode() == Opcodes.DSTORE) ? 2 : 1;
                limit = Math.max(limit, variable.var + width);
            } else if (instruction instanceof IincInsnNode) {
                limit = Math.max(limit, ((IincInsnNode) instruction).var + 1);
            }
        }
        return Math.max(limit, method.maxLocals);
    }

    /**
     * Rebuilds every existing frame as an explicit F_FULL frame with the
     * invocation-local token at its final slot. Relative compressed frames
     * cannot retain an appended local through F_CHOP, so normalizing all
     * forms here is less error-prone than trying to patch only F_APPEND.
     *
     * <p>No type hierarchy is inspected: frame object values are copied as
     * their existing internal names/labels. The only new verification type is
     * java/lang/Object, which is valid for the callback result.</p>
     */
    private static FramePlan planFrames(ClassNode classNode, MethodNode method,
                                        int tokenIndex) {
        List<FrameUpdate> updates = new ArrayList<FrameUpdate>();
        List<Object> previousLocals = initialLocals(classNode);

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof FrameNode)) {
                continue;
            }

            FrameNode frame = (FrameNode) instruction;
            List<Object> locals;
            List<Object> stack;
            switch (frame.type) {
                case Opcodes.F_NEW:
                case Opcodes.F_FULL:
                    locals = normalizeFrameValues(frame.local);
                    stack = normalizeFrameValues(frame.stack);
                    break;
                case Opcodes.F_APPEND:
                    // Reconstruct the original state first; the token is
                    // appended only after relative-frame changes are applied.
                    locals = new ArrayList<Object>(previousLocals);
                    locals.addAll(normalizeFrameValues(frame.local));
                    stack = Collections.emptyList();
                    break;
                case Opcodes.F_CHOP:
                    int chopped = frame.local == null ? 0 : frame.local.size();
                    if (chopped < 1 || chopped > previousLocals.size()) {
                        throw new IllegalArgumentException("invalid F_CHOP frame");
                    }
                    locals = new ArrayList<Object>(
                            previousLocals.subList(0, previousLocals.size() - chopped));
                    stack = Collections.emptyList();
                    break;
                case Opcodes.F_SAME:
                    locals = new ArrayList<Object>(previousLocals);
                    stack = Collections.emptyList();
                    break;
                case Opcodes.F_SAME1:
                    locals = new ArrayList<Object>(previousLocals);
                    stack = normalizeFrameValues(frame.stack);
                    if (stack.size() != 1) {
                        throw new IllegalArgumentException("invalid F_SAME1 frame");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported stack-map frame type "
                            + frame.type);
            }

            List<Object> adjustedLocals = appendToken(locals, tokenIndex);
            updates.add(new FrameUpdate(frame, adjustedLocals, stack));
            previousLocals = locals;
        }
        return new FramePlan(updates);
    }

    private static List<Object> initialLocals(ClassNode classNode) {
        List<Object> locals = new ArrayList<Object>();
        locals.add(classNode.name);
        locals.add(PACK_REPOSITORY);
        return locals;
    }

    private static List<Object> normalizeFrameValues(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<Object>();
        }
        List<Object> normalized = new ArrayList<Object>(values.size());
        for (Object value : values) {
            if (value == null) {
                throw new IllegalArgumentException("null stack-map value");
            }
            // ASM encodes each category-2 value as one frame entry. Preserve
            // every explicit entry, including TOP gaps in local arrays.
            normalized.add(value);
        }
        return normalized;
    }

    private static boolean isCategory2(Object value) {
        return value instanceof Integer
                && (Opcodes.LONG == ((Integer) value)
                || Opcodes.DOUBLE == ((Integer) value));
    }

    private static List<Object> appendToken(List<Object> original, int tokenIndex) {
        // Frame lists count verification entries (long/double once), while
        // the JVM local index counts their two physical slots.
        List<Object> adjusted = new ArrayList<Object>(original);
        int slots = localSlotCount(adjusted);
        if (slots > tokenIndex) {
            throw new IllegalArgumentException("frame local exceeds token slot");
        }
        while (slots < tokenIndex) {
            adjusted.add(Opcodes.TOP);
            slots++;
        }
        adjusted.add(FRAME_TOKEN);
        return adjusted;
    }

    private static int localSlotCount(List<Object> locals) {
        int slots = 0;
        for (Object local : locals) {
            slots += isCategory2(local) ? 2 : 1;
        }
        return slots;
    }

    private static InsnList newBeforeInstructions(String selectedFieldName, int tokenIndex) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new LdcInsnNode(selectedFieldName));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CALLBACK_OWNER,
                BEFORE_NAME, BEFORE_DESC, false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, tokenIndex));
        return instructions;
    }

    private static InsnList newAfterInstructions(Binding binding, int tokenIndex) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, tokenIndex));
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

    private static final class FramePlan {
        private final List<FrameUpdate> updates;

        private FramePlan(List<FrameUpdate> updates) {
            this.updates = updates;
        }

        private void apply() {
            for (FrameUpdate update : updates) {
                update.frame.type = Opcodes.F_FULL;
                update.frame.local = update.locals;
                update.frame.stack = update.stack;
            }
        }
    }

    private static final class FrameUpdate {
        private final FrameNode frame;
        private final List<Object> locals;
        private final List<Object> stack;

        private FrameUpdate(FrameNode frame, List<Object> locals, List<Object> stack) {
            this.frame = frame;
            this.locals = locals;
            this.stack = stack;
        }
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
