package org.webbeans.scene3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mutable surface description shared by any number of meshes and scenes. Materials have a global id; every
 * scene sends the definition once and then receives a {@code mat} update whenever a property changes, so
 * "turn all selected planets red" is one small message however many meshes share the material.
 *
 * <p>Colors are {@code 0xRRGGBB} ints.</p>
 */
public class Material {

    public enum Type { BASIC, LAMBERT, PHONG, STANDARD, PHYSICAL }

    public enum Side { FRONT, BACK, DOUBLE }

    private static final AtomicInteger IDS = new AtomicInteger();

    private final String id = "m" + IDS.incrementAndGet();
    private final Set<Scene3D> users = Collections.newSetFromMap(new WeakHashMap<Scene3D, Boolean>());
    private Type type;
    private int color = 0xffffff;
    private double opacity = 1;
    private boolean transparent;
    private double metalness = 0.1;
    private double roughness = 0.6;
    private int emissive;
    private double emissiveIntensity = 1;
    private double shininess = 30;
    private Texture map;
    private Texture normalMap;
    private Texture emissiveMap;
    private Side side = Side.FRONT;
    private boolean flatShading;
    private boolean depthTest = true;
    private boolean wireframe;

    public Material() {
        this(Type.STANDARD);
    }

    public Material(Type type) {
        this.type = type == null ? Type.STANDARD : type;
    }

    public static Material standard(int color) {
        return new Material(Type.STANDARD).setColor(color);
    }

    public static Material basic(int color) {
        return new Material(Type.BASIC).setColor(color);
    }

    public static Material phong(int color) {
        return new Material(Type.PHONG).setColor(color);
    }

    public static Material lambert(int color) {
        return new Material(Type.LAMBERT).setColor(color);
    }

    public static Material wireframe(int color) {
        return new Material(Type.BASIC).setColor(color).setWireframe(true);
    }

    /** Global id ({@code m7}). */
    public final String getId() {
        return id;
    }

    /** The wire kind: {@code mesh}, {@code line} or {@code points}. */
    String kind() {
        return "mesh";
    }

    public Type getType() {
        return type;
    }

    public Material setType(Type t) {
        type = t == null ? Type.STANDARD : t;
        return changed();
    }

    public int getColor() {
        return color;
    }

    public Material setColor(int rgb) {
        color = rgb & 0xffffff;
        return changed();
    }

    public double getOpacity() {
        return opacity;
    }

    /** 0..1; values below 1 make the material transparent. */
    public Material setOpacity(double o) {
        opacity = Math.max(0, Math.min(1, o));
        if (opacity < 1) {
            transparent = true;
        }
        return changed();
    }

    public boolean isTransparent() {
        return transparent;
    }

    public Material setTransparent(boolean b) {
        transparent = b;
        return changed();
    }

    public double getMetalness() {
        return metalness;
    }

    public Material setMetalness(double m) {
        metalness = m;
        return changed();
    }

    public double getRoughness() {
        return roughness;
    }

    public Material setRoughness(double r) {
        roughness = r;
        return changed();
    }

    public int getEmissive() {
        return emissive;
    }

    public Material setEmissive(int rgb) {
        emissive = rgb & 0xffffff;
        return changed();
    }

    public Material setEmissive(int rgb, double intensity) {
        emissive = rgb & 0xffffff;
        emissiveIntensity = intensity;
        return changed();
    }

    public double getEmissiveIntensity() {
        return emissiveIntensity;
    }

    public double getShininess() {
        return shininess;
    }

    /** Phong only. */
    public Material setShininess(double s) {
        shininess = s;
        return changed();
    }

    public Texture getMap() {
        return map;
    }

    public Material setMap(Texture t) {
        map = t;
        return changed();
    }

    public Texture getNormalMap() {
        return normalMap;
    }

    public Material setNormalMap(Texture t) {
        normalMap = t;
        return changed();
    }

    public Texture getEmissiveMap() {
        return emissiveMap;
    }

    public Material setEmissiveMap(Texture t) {
        emissiveMap = t;
        return changed();
    }

    public Side getSide() {
        return side;
    }

    public Material setSide(Side s) {
        side = s == null ? Side.FRONT : s;
        return changed();
    }

    public boolean isFlatShading() {
        return flatShading;
    }

    public Material setFlatShading(boolean b) {
        flatShading = b;
        return changed();
    }

    public boolean isDepthTest() {
        return depthTest;
    }

    public Material setDepthTest(boolean b) {
        depthTest = b;
        return changed();
    }

    public boolean isWireframe() {
        return wireframe;
    }

    public Material setWireframe(boolean b) {
        wireframe = b;
        return changed();
    }

    /** Textures this material references (registered with a scene before the definition is sent). */
    List<Texture> textures() {
        List<Texture> l = new ArrayList<Texture>(3);
        if (map != null) {
            l.add(map);
        }
        if (normalMap != null) {
            l.add(normalMap);
        }
        if (emissiveMap != null) {
            l.add(emissiveMap);
        }
        return l;
    }

    /** The full definition. */
    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("kind", kind());
        m.put("type", type.name().toLowerCase());
        m.put("color", color);
        if (opacity < 1) {
            m.put("opacity", opacity);
        }
        if (transparent) {
            m.put("transparent", true);
        }
        if (type == Type.STANDARD || type == Type.PHYSICAL) {
            m.put("metalness", metalness);
            m.put("roughness", roughness);
        }
        if (type == Type.PHONG) {
            m.put("shininess", shininess);
        }
        if (emissive != 0) {
            m.put("emissive", emissive);
            m.put("emissiveIntensity", emissiveIntensity);
        }
        if (map != null) {
            m.put("map", map.getId());
        }
        if (normalMap != null) {
            m.put("normalMap", normalMap.getId());
        }
        if (emissiveMap != null) {
            m.put("emissiveMap", emissiveMap.getId());
        }
        if (side != Side.FRONT) {
            m.put("side", side.name().toLowerCase());
        }
        if (flatShading) {
            m.put("flatShading", true);
        }
        if (!depthTest) {
            m.put("depthTest", false);
        }
        if (wireframe) {
            m.put("wireframe", true);
        }
        return m;
    }

    final void addUser(Scene3D s) {
        synchronized (users) {
            users.add(s);
        }
    }

    final void removeUser(Scene3D s) {
        synchronized (users) {
            users.remove(s);
        }
    }

    /** Tells every scene showing this material to re-send its definition with the next flush. */
    protected final Material changed() {
        List<Scene3D> copy;
        synchronized (users) {
            copy = new ArrayList<Scene3D>(users);
        }
        for (Scene3D s : copy) {
            s.materialChanged(this);
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + " " + String.format("#%06x", color) + "]";
    }
}
