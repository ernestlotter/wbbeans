package org.webbeans.scene3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.ui.BlobStore;

/**
 * One geometry and material drawn {@code count} times with per-instance transforms (and optional colors):
 * ten thousand asteroids are one draw call and one node. Transforms are 4x4 column-major matrices; the
 * convenience setters build them from position/rotation/scale. Small sets travel as JSON; large ones as a blob.
 * Instances are pickable: {@link PickEvent#getInstance()} tells which one was hit.
 */
public class InstancedMesh extends Node3D {

    /** Above this many instances the transform array is sent through the blob store. */
    static final int BLOB_THRESHOLD = 512;

    private Geometry geometry;
    private Material material;
    private int count;
    private float[] transforms;
    private float[] colors;
    private final List<Integer> dirty = new ArrayList<Integer>();
    private boolean allDirty = true;

    public InstancedMesh(Geometry geometry, Material material, int count) {
        this.geometry = geometry;
        this.material = material;
        setCount(count);
    }

    @Override
    String type() {
        return "imesh";
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public InstancedMesh setGeometry(Geometry g) {
        geometry = g;
        if (getScene() != null) {
            getScene().register(g);
        }
        changed("g", g == null ? null : g.getId());
        return this;
    }

    public Material getMaterial() {
        return material;
    }

    public InstancedMesh setMaterial(Material m) {
        material = m;
        if (getScene() != null) {
            getScene().register(m);
        }
        changed("m", m == null ? null : m.getId());
        return this;
    }

    public int getCount() {
        return count;
    }

    /** Resizes; existing transforms are kept, new instances start as identity. */
    public InstancedMesh setCount(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("count " + n);
        }
        float[] t = new float[n * 16];
        for (int i = 0; i < n; i++) {
            if (transforms != null && (i + 1) * 16 <= transforms.length) {
                System.arraycopy(transforms, i * 16, t, i * 16, 16);
            } else {
                t[i * 16] = 1;
                t[i * 16 + 5] = 1;
                t[i * 16 + 10] = 1;
                t[i * 16 + 15] = 1;
            }
        }
        if (colors != null) {
            float[] c = new float[n * 3];
            System.arraycopy(colors, 0, c, 0, Math.min(colors.length, c.length));
            colors = c;
        }
        transforms = t;
        count = n;
        allDirty = true;
        dirty.clear();
        changed("inst", null);
        return this;
    }

    /** The raw 16*count column-major matrices (a copy). */
    public float[] getTransforms() {
        return transforms.clone();
    }

    public InstancedMesh setInstanceTransforms(float[] matrices) {
        if (matrices == null || matrices.length != count * 16) {
            throw new IllegalArgumentException("expected " + (count * 16) + " floats");
        }
        transforms = matrices.clone();
        allDirty = true;
        dirty.clear();
        changed("inst", null);
        return this;
    }

    public InstancedMesh setInstanceTransform(int i, Vector3 position, Quaternion rotation, Vector3 scale) {
        compose(transforms, i * 16, position == null ? Vector3.ZERO : position, rotation == null ? Quaternion.IDENTITY : rotation,
                scale == null ? Vector3.ONE : scale);
        markDirty(i);
        return this;
    }

    public InstancedMesh setInstancePosition(int i, Vector3 position) {
        int o = i * 16;
        transforms[o + 12] = (float) position.x;
        transforms[o + 13] = (float) position.y;
        transforms[o + 14] = (float) position.z;
        markDirty(i);
        return this;
    }

    /** Per-instance tint (rgb 0..1), multiplied with the material color. */
    public InstancedMesh setInstanceColor(int i, int rgb) {
        if (colors == null) {
            colors = new float[count * 3];
            java.util.Arrays.fill(colors, 1f);
        }
        colors[i * 3] = ((rgb >> 16) & 0xff) / 255f;
        colors[i * 3 + 1] = ((rgb >> 8) & 0xff) / 255f;
        colors[i * 3 + 2] = (rgb & 0xff) / 255f;
        markDirty(i);
        return this;
    }

    private void markDirty(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("instance " + i + " of " + count);
        }
        if (!allDirty) {
            if (!dirty.contains(i)) {
                dirty.add(i);
            }
            if (dirty.size() > count / 4 + 8) {
                allDirty = true;
                dirty.clear();
            }
        }
        changed("inst", null);
    }

    /** Writes position * rotation * scale as a column-major 4x4 matrix. */
    static void compose(float[] out, int o, Vector3 p, Quaternion q, Vector3 s) {
        double x = q.x, y = q.y, z = q.z, w = q.w;
        double x2 = x + x, y2 = y + y, z2 = z + z;
        double xx = x * x2, xy = x * y2, xz = x * z2;
        double yy = y * y2, yz = y * z2, zz = z * z2;
        double wx = w * x2, wy = w * y2, wz = w * z2;
        out[o] = (float) ((1 - (yy + zz)) * s.x);
        out[o + 1] = (float) ((xy + wz) * s.x);
        out[o + 2] = (float) ((xz - wy) * s.x);
        out[o + 3] = 0;
        out[o + 4] = (float) ((xy - wz) * s.y);
        out[o + 5] = (float) ((1 - (xx + zz)) * s.y);
        out[o + 6] = (float) ((yz + wx) * s.y);
        out[o + 7] = 0;
        out[o + 8] = (float) ((xz + wy) * s.z);
        out[o + 9] = (float) ((yz - wx) * s.z);
        out[o + 10] = (float) ((1 - (xx + yy)) * s.z);
        out[o + 11] = 0;
        out[o + 12] = (float) p.x;
        out[o + 13] = (float) p.y;
        out[o + 14] = (float) p.z;
        out[o + 15] = 1;
    }

    /** The instance payload for a {@code set inst} op or the {@code add} op; clears the dirty bookkeeping. */
    Map<String, Object> instanceJson(boolean full) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("count", count);
        if (full || allDirty) {
            if (count > BLOB_THRESHOLD) {
                ByteBuffer bb = ByteBuffer.allocate(transforms.length * 4 + (colors == null ? 0 : colors.length * 4)).order(ByteOrder.LITTLE_ENDIAN);
                for (float f : transforms) {
                    bb.putFloat(f);
                }
                if (colors != null) {
                    for (float f : colors) {
                        bb.putFloat(f);
                    }
                }
                m.put("blob", BlobStore.getDefault().put(bb.array(), "application/octet-stream"));
                m.put("colors", colors != null);
            } else {
                m.put("xf", floats(transforms));
                if (colors != null) {
                    m.put("col", floats(colors));
                }
            }
        } else {
            List<Object> upd = new ArrayList<Object>();
            for (Integer i : dirty) {
                Map<String, Object> u = new LinkedHashMap<String, Object>();
                u.put("i", i);
                List<Float> xf = new ArrayList<Float>(16);
                for (int k = 0; k < 16; k++) {
                    xf.add(transforms[i * 16 + k]);
                }
                u.put("xf", xf);
                if (colors != null) {
                    u.put("col", java.util.Arrays.asList(colors[i * 3], colors[i * 3 + 1], colors[i * 3 + 2]));
                }
                upd.add(u);
            }
            m.put("upd", upd);
        }
        allDirty = false;
        dirty.clear();
        return m;
    }

    private static List<Float> floats(float[] a) {
        List<Float> l = new ArrayList<Float>(a.length);
        for (float f : a) {
            l.add(f);
        }
        return l;
    }

    @Override
    void typeJson(Map<String, Object> m, Scene3D into) {
        into.register(geometry);
        into.register(material);
        m.put("g", geometry == null ? null : geometry.getId());
        m.put("m", material == null ? null : material.getId());
        m.put("inst", instanceJson(true));
    }
}
