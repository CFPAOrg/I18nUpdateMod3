package i18nupdatemod.modlauncher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge still boots through ModLauncher on Minecraft 26.1, so this service stays the Forge
 * entrypoint there. Two things changed on that version and are pinned here.
 */
class ModLauncherServiceTest {
    /**
     * Forge 26.1 launches with only {@code --launchTarget forge_client}; the {@code --fml.mcversion}
     * argument this code reads first is gone, so the {@code /forge_version.json} fallback is now the
     * only source of the version. It must degrade to null rather than throwing when neither is
     * reachable, because {@code initialize} runs inside the loader.
     */
    @Test
    void reportsNoVersionOutsideForgeInsteadOfThrowing() throws Exception {
        assertNull(invoke("getMinecraftVersion"));
    }

    /**
     * The loader label picks the Forge or NeoForge pack, so it must not report NeoForge when the
     * NeoForge FMLLoader is absent.
     */
    @Test
    void doesNotClaimNeoForgeWhenItIsAbsent() throws Exception {
        assertFalse((Boolean) invoke("isNeoForge"));
    }

    /**
     * FMLLoader is only needed as a resource anchor. Initializing it drags in the loader's own
     * runtime, and on Forge 26.1 those classes are compiled for a newer Java than older launchers
     * run, which raises an Error that {@code catch (Exception)} would not stop. Loading it
     * non-initializing keeps such failures out of the launch path.
     */
    @Test
    void looksUpClassesWithoutRunningStaticInitializers() throws Exception {
        Method loadClass = ModLauncherService.class.getDeclaredMethod("loadClass", String.class);
        loadClass.setAccessible(true);

        assertNull(loadClass.invoke(null, "does.not.Exist"));
        assertTrue(ExplodesOnInit.class == loadClass.invoke(null, ExplodesOnInit.class.getName()),
                "must return the class without initializing it");
        // Held in a separate class, since reading a field of ExplodesOnInit would itself initialize it.
        assertFalse(InitFlag.reached, "static initializer must not have run");
    }

    private static Object invoke(String name) throws Exception {
        Method method = ModLauncherService.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(new ModLauncherService());
    }

    /** Records initialization out of band, so observing it does not cause it. */
    static class InitFlag {
        static boolean reached;
    }

    /** Stands in for an FMLLoader whose static initializer fails. */
    static class ExplodesOnInit {
        static {
            InitFlag.reached = true;
            if (Boolean.parseBoolean("true")) {
                throw new RuntimeException("static initializer must not be reached");
            }
        }
    }
}
