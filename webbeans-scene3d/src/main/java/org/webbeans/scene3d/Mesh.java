package org.webbeans.scene3d;

import java.util.Map;

/** A geometry drawn with a material. Both are shared by id; swapping either is one small op. */
public class Mesh extends Node3D {

    private Geometry geometry;
    private Material material;

    public Mesh(Geometry geometry, Material material) {
        this.geometry = geometry;
        this.material = material;
    }

    public Mesh(String name, Geometry geometry, Material material) {
        this(geometry, material);
        setName(name);
    }

    @Override
    String type() {
        return "mesh";
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public Mesh setGeometry(Geometry g) {
        if (g == null ? geometry == null : g.equals(geometry)) {
            return this;
        }
        geometry = g;
        Scene3D s = getScene();
        if (s != null) {
            s.register(g);
        }
        changed("g", g == null ? null : g.getId());
        return this;
    }

    public Material getMaterial() {
        return material;
    }

    public Mesh setMaterial(Material m) {
        if (m == material) {
            return this;
        }
        material = m;
        Scene3D s = getScene();
        if (s != null) {
            s.register(m);
        }
        changed("m", m == null ? null : m.getId());
        return this;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        into.register(geometry);
        into.register(material);
        m.put("g", geometry == null ? null : geometry.getId());
        m.put("m", material == null ? null : material.getId());
    }
}
