package org.webbeans.scene3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.Json;
import org.webbeans.ui.BlobStore;

/**
 * An immutable, content-addressed geometry. Primitives are described by their parameters and built in the
 * browser; arbitrary meshes are uploaded once as a binary blob ({@code /wb/blob/<sha256>}) and referenced by
 * id afterwards, so two scenes (or two sessions) showing the same model never transfer it twice.
 *
 * <p>Blob layout: a little-endian {@code u32} header length, a JSON header
 * {@code {attrs:{position:{type:"f32",size:3,count,offset},normal,uv,color},index:{type:"u32",count,offset}}}
 * whose offsets are relative to the start of the data section that follows the header, then the arrays.</p>
 */
public final class Geometry {

    private final String id;
    private final Map<String, Object> json;
    private final int vertexCount;
    private final int triangleCount;

    private Geometry(String id, Map<String, Object> json, int vertexCount, int triangleCount) {
        this.id = id;
        this.json = json;
        this.vertexCount = vertexCount;
        this.triangleCount = triangleCount;
    }

    /** Scene-independent id ({@code g...}). */
    public String getId() {
        return id;
    }

    /** Vertices (0 for primitives, whose count depends on the segments). */
    public int getVertexCount() {
        return vertexCount;
    }

    public int getTriangleCount() {
        return triangleCount;
    }

    /** The definition sent to the client once per scene. */
    Map<String, Object> toJson() {
        return json;
    }

    // ---- primitives ----------------------------------------------------------------------------------------------

    public static Geometry box(double width, double height, double depth) {
        return prim("box", width, height, depth);
    }

    public static Geometry sphere(double radius) {
        return sphere(radius, 32, 16);
    }

    public static Geometry sphere(double radius, int widthSegments, int heightSegments) {
        return prim("sphere", radius, widthSegments, heightSegments);
    }

    public static Geometry plane(double width, double height) {
        return prim("plane", width, height);
    }

    public static Geometry cylinder(double radiusTop, double radiusBottom, double height, int segments) {
        return prim("cylinder", radiusTop, radiusBottom, height, segments);
    }

    public static Geometry cone(double radius, double height, int segments) {
        return prim("cone", radius, height, segments);
    }

    public static Geometry torus(double radius, double tube, int radialSegments, int tubularSegments) {
        return prim("torus", radius, tube, radialSegments, tubularSegments);
    }

    public static Geometry circle(double radius, int segments) {
        return prim("circle", radius, segments);
    }

    public static Geometry ring(double innerRadius, double outerRadius, int segments) {
        return prim("ring", innerRadius, outerRadius, segments);
    }

    private static Geometry prim(String name, Number... args) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("prim", name);
        m.put("args", Arrays.asList((Object[]) args));
        String id = "g" + BlobStore.hash(Json.write(m).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        return new Geometry(id, m, 0, 0);
    }

    // ---- buffers -------------------------------------------------------------------------------------------------

    /** Starts an arbitrary triangle mesh. */
    public static Builder buffer() {
        return new Builder();
    }

    /** Collects vertex attributes and indices; {@link #build()} uploads them to the blob store. */
    public static final class Builder {
        private float[] positions = new float[0];
        private float[] normals;
        private float[] uvs;
        private float[] colors;
        private int[] indices;
        private boolean computeNormals;

        Builder() {
        }

        /** xyz triples. */
        public Builder positions(float[] xyz) {
            positions = xyz == null ? new float[0] : xyz;
            return this;
        }

        public Builder positions(List<Vector3> pts) {
            float[] a = new float[pts.size() * 3];
            int i = 0;
            for (Vector3 v : pts) {
                a[i++] = (float) v.x;
                a[i++] = (float) v.y;
                a[i++] = (float) v.z;
            }
            return positions(a);
        }

        /** xyz triples, one per vertex. */
        public Builder normals(float[] xyz) {
            normals = xyz;
            return this;
        }

        /** Let the client compute smooth vertex normals when none are given. */
        public Builder computeNormals(boolean b) {
            computeNormals = b;
            return this;
        }

        /** uv pairs, one per vertex. */
        public Builder uvs(float[] uv) {
            uvs = uv;
            return this;
        }

        /** rgb triples (0..1), one per vertex. */
        public Builder colors(float[] rgb) {
            colors = rgb;
            return this;
        }

        /** Triangle indices; omit for a non-indexed triangle soup. */
        public Builder indices(int[] idx) {
            indices = idx;
            return this;
        }

        public Geometry build() {
            int count = positions.length / 3;
            Map<String, Object> attrs = new LinkedHashMap<String, Object>();
            List<float[]> arrays = new ArrayList<float[]>();
            int offset = 0;
            offset = attr(attrs, arrays, "position", positions, 3, offset);
            if (normals != null && normals.length == positions.length) {
                offset = attr(attrs, arrays, "normal", normals, 3, offset);
            }
            if (uvs != null && uvs.length == count * 2) {
                offset = attr(attrs, arrays, "uv", uvs, 2, offset);
            }
            if (colors != null && colors.length == positions.length) {
                offset = attr(attrs, arrays, "color", colors, 3, offset);
            }
            Map<String, Object> header = new LinkedHashMap<String, Object>();
            header.put("attrs", attrs);
            int indexBytes = 0;
            if (indices != null && indices.length > 0) {
                Map<String, Object> ix = new LinkedHashMap<String, Object>();
                ix.put("type", "u32");
                ix.put("count", indices.length);
                ix.put("offset", offset);
                header.put("index", ix);
                indexBytes = indices.length * 4;
            }
            if (computeNormals && !attrs.containsKey("normal")) {
                header.put("computeNormals", true);
            }
            byte[] hdr = Json.write(header).getBytes(StandardCharsets.UTF_8);
            ByteBuffer bb = ByteBuffer.allocate(4 + hdr.length + offset + indexBytes).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(hdr.length);
            bb.put(hdr);
            for (float[] a : arrays) {
                for (float f : a) {
                    bb.putFloat(f);
                }
            }
            if (indexBytes > 0) {
                for (int i : indices) {
                    bb.putInt(i);
                }
            }
            byte[] bytes = bb.array();
            String blob = BlobStore.getDefault().put(bytes, "application/octet-stream");
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("blob", blob);
            m.put("bytes", bytes.length);
            int tris = indices != null && indices.length > 0 ? indices.length / 3 : count / 3;
            return new Geometry("g" + blob.substring(0, 16), m, count, tris);
        }

        private static int attr(Map<String, Object> attrs, List<float[]> arrays, String name, float[] a, int size, int offset) {
            Map<String, Object> d = new LinkedHashMap<String, Object>();
            d.put("type", "f32");
            d.put("size", size);
            d.put("count", a.length / size);
            d.put("offset", offset);
            attrs.put(name, d);
            arrays.add(a);
            return offset + a.length * 4;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Geometry && ((Geometry) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Geometry[" + id + " " + json + "]";
    }
}
