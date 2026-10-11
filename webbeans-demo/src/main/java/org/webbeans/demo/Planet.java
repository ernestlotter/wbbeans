package org.webbeans.demo;

import org.webbeans.core.i18n.Messages;
import org.webbeans.ui.Labeled;
import org.webbeans.ui.Resource;

/** Demo data: a mutable planet of the session's {@link SolarSystem}. */
public final class Planet {

    /**
     * Planet classes are {@link Labeled}: property editors, the outline column, combo boxes and table cells show the
     * localised name and the icon instead of {@code GAS_GIANT}. (An enum you cannot change gets the same through a
     * {@code Type.GAS_GIANT=…} bundle key or {@code PropertyEditors.registerEnum}.)
     */
    @Messages({"TYPE_ROCKY=Rocky", "TYPE_GAS_GIANT=Gas giant", "TYPE_ICE_GIANT=Ice giant", "TYPE_DWARF=Dwarf planet"})
    public enum Type implements Labeled {
        ROCKY("planet"), GAS_GIANT("orbit"), ICE_GIANT("sun"), DWARF("dwarf");

        private final String icon;

        Type(String icon) {
            this.icon = icon;
        }

        @Override
        public String getDisplayName() {
            switch (this) {
                case ROCKY: return Bundle.TYPE_ROCKY();
                case GAS_GIANT: return Bundle.TYPE_GAS_GIANT();
                case ICE_GIANT: return Bundle.TYPE_ICE_GIANT();
                default: return Bundle.TYPE_DWARF();
            }
        }

        @Override
        public Resource getIcon() {
            return Resource.of("org/webbeans/demo/icons/" + icon + ".svg");
        }
    }

    private String name;
    private double gravity;
    private int moons;
    private Type type;
    private boolean explored;

    public Planet(String name, double gravity, int moons, Type type, boolean explored) {
        this.name = name;
        this.gravity = gravity;
        this.moons = moons;
        this.type = type;
        this.explored = explored;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getGravity() {
        return gravity;
    }

    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    public int getMoons() {
        return moons;
    }

    public void setMoons(int moons) {
        this.moons = moons;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isExplored() {
        return explored;
    }

    public void setExplored(boolean explored) {
        this.explored = explored;
    }

    @Override
    public String toString() {
        return name;
    }
}
