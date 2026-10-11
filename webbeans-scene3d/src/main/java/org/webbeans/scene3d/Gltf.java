package org.webbeans.scene3d;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.webbeans.core.json.Json;

/**
 * A small glTF 2.0 reader: binary ({@code .glb}) or JSON with embedded ({@code data:}) buffers and images.
 * Reads the node hierarchy with transforms, triangle primitives (positions, normals, uvs, colors, indices),
 * metallic-roughness materials with base color, normal and emissive textures. Not read: external URIs,
 * animations, skins, morph targets, cameras, extensions.
 *
 * <pre>
 * Group ship = Gltf.load(getClass().getResourceAsStream("ship.glb"));
 * scene.getRoot().add(ship);
 * </pre>
 */
public final class Gltf {

    private final Map<String, Object> doc;
    private final List<byte[]> buffers = new ArrayList<byte[]>();
    private final Map<Integer, Material> materials = new HashMap<Integer, Material>();
    private final Map<Integer, Texture> textures = new HashMap<Integer, Texture>();
    private final Map<String, Geometry> geometries = new HashMap<String, Geometry>();
    private final Material defaultMaterial = Material.standard(0xcccccc);

    private Gltf(Map<String, Object> doc) {
        this.doc = doc;
    }

    /** Reads a {@code .glb} or {@code .gltf} stream into a group (the asset's default scene). */
    public static Group load(InputStream in) throws IOException {
        return load(readAll(in));
    }

    public static Group load(byte[] bytes) throws IOException {
        Gltf g;
        if (bytes.length >= 12 && bytes[0] == 'g' && bytes[1] == 'l' && bytes[2] == 'T' && bytes[3] == 'F') {
            g = parseGlb(bytes);
        } else {
            g = new Gltf(Json.parseObject(new String(bytes, StandardCharsets.UTF_8)));
            g.loadBuffers();
        }
        return g.build();
    }

    private static Gltf parseGlb(byte[] bytes) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(4);
        int version = bb.getInt();
        if (version != 2) {
            throw new IOException("Unsupported glTF version " + version);
        }
        bb.getInt(); // total length
        Map<String, Object> json = null;
        byte[] bin = null;
        while (bb.remaining() >= 8) {
            int len = bb.getInt();
            int type = bb.getInt();
            byte[] chunk = new byte[len];
            bb.get(chunk);
            if (type == 0x4E4F534A) {
                json = Json.parseObject(new String(chunk, StandardCharsets.UTF_8));
            } else if (type == 0x004E4942) {
                bin = chunk;
            }
        }
        if (json == null) {
            throw new IOException("GLB without a JSON chunk");
        }
        Gltf g = new Gltf(json);
        g.loadBuffers(bin);
        return g;
    }

    private void loadBuffers() throws IOException {
        loadBuffers(null);
    }

    private void loadBuffers(byte[] bin) throws IOException {
        List<Object> bufs = Json.array(doc, "buffers");
        if (bufs == null) {
            return;
        }
        for (int i = 0; i < bufs.size(); i++) {
            Map<String, Object> b = asMap(bufs.get(i));
            String uri = Json.string(b, "uri");
            if (uri == null) {
                if (i == 0 && bin != null) {
                    buffers.add(bin);
                    continue;
                }
                throw new IOException("buffer " + i + " has no data");
            }
            buffers.add(dataUri(uri));
        }
    }

    private static byte[] dataUri(String uri) throws IOException {
        if (!uri.startsWith("data:")) {
            throw new IOException("External buffer/image URIs are not supported: " + uri);
        }
        int comma = uri.indexOf(',');
        if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) {
            throw new IOException("Only base64 data URIs are supported");
        }
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    // ---- building ------------------------------------------------------------------------------------------------

    private Group build() throws IOException {
        Group root = new Group("gltf");
        List<Object> scenes = Json.array(doc, "scenes");
        List<Object> nodes = Json.array(doc, "nodes");
        if (scenes == null || scenes.isEmpty() || nodes == null) {
            return root;
        }
        int sceneIndex = (int) Json.integer(doc, "scene", 0);
        Map<String, Object> scene = asMap(scenes.get(Math.min(sceneIndex, scenes.size() - 1)));
        List<Object> roots = Json.array(scene, "nodes");
        if (roots != null) {
            for (Object o : roots) {
                root.add(buildNode(((Number) o).intValue(), nodes, 0));
            }
        }
        return root;
    }

    private Node3D buildNode(int index, List<Object> nodes, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("Node graph too deep (cycle?)");
        }
        Map<String, Object> n = asMap(nodes.get(index));
        Group g = new Group(Json.string(n, "name"));
        applyTransform(g, n);
        Object mesh = n.get("mesh");
        if (mesh instanceof Number) {
            int mi = ((Number) mesh).intValue();
            Map<String, Object> m = asMap(Json.array(doc, "meshes").get(mi));
            List<Object> prims = Json.array(m, "primitives");
            int i = 0;
            for (Object p : prims) {
                Map<String, Object> prim = asMap(p);
                int mode = (int) Json.integer(prim, "mode", 4);
                if (mode != 4) {
                    continue; // only TRIANGLES
                }
                Geometry geom = geometry(prim);
                Object matIdx = prim.get("material");
                Material mat = matIdx instanceof Number ? material(((Number) matIdx).intValue()) : defaultMaterial;
                Mesh mesh3 = new Mesh(geom, mat);
                String name = Json.string(m, "name");
                mesh3.setName(name == null ? "mesh" + mi : prims.size() > 1 ? name + "#" + i : name);
                g.add(mesh3);
                i++;
            }
        }
        List<Object> children = Json.array(n, "children");
        if (children != null) {
            for (Object c : children) {
                g.add(buildNode(((Number) c).intValue(), nodes, depth + 1));
            }
        }
        return g;
    }

    private static void applyTransform(Node3D g, Map<String, Object> n) {
        List<Object> matrix = Json.array(n, "matrix");
        if (matrix != null && matrix.size() == 16) {
            double[] m = new double[16];
            for (int i = 0; i < 16; i++) {
                m[i] = ((Number) matrix.get(i)).doubleValue();
            }
            decompose(g, m);
            return;
        }
        List<Object> t = Json.array(n, "translation");
        if (t != null) {
            g.setPosition(vec(t));
        }
        List<Object> r = Json.array(n, "rotation");
        if (r != null && r.size() == 4) {
            g.setRotation(new Quaternion(num(r.get(0)), num(r.get(1)), num(r.get(2)), num(r.get(3))));
        }
        List<Object> s = Json.array(n, "scale");
        if (s != null) {
            g.setScale(vec(s));
        }
    }

    /** Column-major 4x4 → position, rotation, scale. */
    static void decompose(Node3D g, double[] m) {
        double sx = Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
        double sy = Math.sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
        double sz = Math.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);
        double det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[4] * (m[1] * m[10] - m[2] * m[9]) + m[8] * (m[1] * m[6] - m[2] * m[5]);
        if (det < 0) {
            sx = -sx;
        }
        double r00 = m[0] / sx, r01 = m[4] / sy, r02 = m[8] / sz;
        double r10 = m[1] / sx, r11 = m[5] / sy, r12 = m[9] / sz;
        double r20 = m[2] / sx, r21 = m[6] / sy, r22 = m[10] / sz;
        double trace = r00 + r11 + r22;
        double qx, qy, qz, qw;
        if (trace > 0) {
            double s = 0.5 / Math.sqrt(trace + 1);
            qw = 0.25 / s;
            qx = (r21 - r12) * s;
            qy = (r02 - r20) * s;
            qz = (r10 - r01) * s;
        } else if (r00 > r11 && r00 > r22) {
            double s = 2 * Math.sqrt(1 + r00 - r11 - r22);
            qw = (r21 - r12) / s;
            qx = 0.25 * s;
            qy = (r01 + r10) / s;
            qz = (r02 + r20) / s;
        } else if (r11 > r22) {
            double s = 2 * Math.sqrt(1 + r11 - r00 - r22);
            qw = (r02 - r20) / s;
            qx = (r01 + r10) / s;
            qy = 0.25 * s;
            qz = (r12 + r21) / s;
        } else {
            double s = 2 * Math.sqrt(1 + r22 - r00 - r11);
            qw = (r10 - r01) / s;
            qx = (r02 + r20) / s;
            qy = (r12 + r21) / s;
            qz = 0.25 * s;
        }
        g.setPosition(new Vector3(m[12], m[13], m[14]));
        g.setRotation(new Quaternion(qx, qy, qz, qw).normalized());
        g.setScale(new Vector3(sx, sy, sz));
    }

    private Geometry geometry(Map<String, Object> prim) throws IOException {
        Map<String, Object> attrs = Json.object(prim, "attributes");
        if (attrs == null || !(attrs.get("POSITION") instanceof Number)) {
            throw new IOException("primitive without POSITION");
        }
        String key = Json.write(attrs) + "/" + prim.get("indices");
        Geometry g = geometries.get(key);
        if (g != null) {
            return g;
        }
        Geometry.Builder b = Geometry.buffer();
        b.positions(floats(((Number) attrs.get("POSITION")).intValue()));
        if (attrs.get("NORMAL") instanceof Number) {
            b.normals(floats(((Number) attrs.get("NORMAL")).intValue()));
        } else {
            b.computeNormals(true);
        }
        if (attrs.get("TEXCOORD_0") instanceof Number) {
            b.uvs(floats(((Number) attrs.get("TEXCOORD_0")).intValue()));
        }
        if (attrs.get("COLOR_0") instanceof Number) {
            Map<String, Object> acc = accessor(((Number) attrs.get("COLOR_0")).intValue());
            float[] c = floats(((Number) attrs.get("COLOR_0")).intValue());
            if ("VEC4".equals(Json.string(acc, "type"))) {
                float[] rgb = new float[c.length / 4 * 3];
                for (int i = 0, j = 0; i < c.length; i += 4) {
                    rgb[j++] = c[i];
                    rgb[j++] = c[i + 1];
                    rgb[j++] = c[i + 2];
                }
                c = rgb;
            }
            b.colors(c);
        }
        if (prim.get("indices") instanceof Number) {
            b.indices(ints(((Number) prim.get("indices")).intValue()));
        }
        g = b.build();
        geometries.put(key, g);
        return g;
    }

    private Material material(int index) throws IOException {
        Material m = materials.get(index);
        if (m != null) {
            return m;
        }
        Map<String, Object> md = asMap(Json.array(doc, "materials").get(index));
        m = new Material(Material.Type.STANDARD);
        Map<String, Object> pbr = Json.object(md, "pbrMetallicRoughness");
        if (pbr != null) {
            List<Object> bc = Json.array(pbr, "baseColorFactor");
            if (bc != null && bc.size() >= 3) {
                m.setColor(rgb(num(bc.get(0)), num(bc.get(1)), num(bc.get(2))));
                if (bc.size() > 3 && num(bc.get(3)) < 1) {
                    m.setOpacity(num(bc.get(3)));
                }
            }
            m.setMetalness(Json.number(pbr, "metallicFactor", 1));
            m.setRoughness(Json.number(pbr, "roughnessFactor", 1));
            Map<String, Object> bt = Json.object(pbr, "baseColorTexture");
            if (bt != null) {
                m.setMap(texture((int) Json.integer(bt, "index", 0), true));
            }
        }
        List<Object> em = Json.array(md, "emissiveFactor");
        if (em != null && em.size() >= 3) {
            int e = rgb(num(em.get(0)), num(em.get(1)), num(em.get(2)));
            if (e != 0) {
                m.setEmissive(e);
            }
        }
        Map<String, Object> nt = Json.object(md, "normalTexture");
        if (nt != null) {
            m.setNormalMap(texture((int) Json.integer(nt, "index", 0), false));
        }
        Map<String, Object> et = Json.object(md, "emissiveTexture");
        if (et != null) {
            m.setEmissiveMap(texture((int) Json.integer(et, "index", 0), true));
        }
        if (Json.bool(md, "doubleSided")) {
            m.setSide(Material.Side.DOUBLE);
        }
        if ("BLEND".equals(Json.string(md, "alphaMode"))) {
            m.setTransparent(true);
        }
        materials.put(index, m);
        return m;
    }

    private Texture texture(int index, boolean srgb) throws IOException {
        Texture t = textures.get(index);
        if (t != null) {
            return t;
        }
        Map<String, Object> td = asMap(Json.array(doc, "textures").get(index));
        int source = (int) Json.integer(td, "source", -1);
        if (source < 0) {
            throw new IOException("texture " + index + " without source");
        }
        Map<String, Object> img = asMap(Json.array(doc, "images").get(source));
        byte[] bytes;
        String mime = Json.string(img, "mimeType");
        if (img.get("bufferView") instanceof Number) {
            bytes = view(((Number) img.get("bufferView")).intValue());
        } else {
            String uri = Json.string(img, "uri");
            bytes = dataUri(uri);
            if (mime == null && uri.startsWith("data:")) {
                mime = uri.substring(5, uri.indexOf(';'));
            }
        }
        // glTF uv origin is top-left: textures are not flipped
        t = Texture.image(bytes, mime).flipY(false).srgb(srgb).wrap(Texture.Wrap.REPEAT);
        Map<String, Object> sd = td.get("sampler") instanceof Number ? asMap(Json.array(doc, "samplers").get(((Number) td.get("sampler")).intValue())) : null;
        if (sd != null && Json.integer(sd, "wrapS", 10497) == 33071) {
            t = t.wrap(Texture.Wrap.CLAMP);
        }
        textures.put(index, t);
        return t;
    }

    // ---- accessors -----------------------------------------------------------------------------------------------

    private Map<String, Object> accessor(int index) {
        return asMap(Json.array(doc, "accessors").get(index));
    }

    private byte[] view(int index) throws IOException {
        Map<String, Object> bv = asMap(Json.array(doc, "bufferViews").get(index));
        byte[] buf = buffers.get((int) Json.integer(bv, "buffer", 0));
        int off = (int) Json.integer(bv, "byteOffset", 0);
        int len = (int) Json.integer(bv, "byteLength", buf.length - off);
        byte[] out = new byte[len];
        System.arraycopy(buf, off, out, 0, len);
        return out;
    }

    private static int components(String type) {
        if ("SCALAR".equals(type)) {
            return 1;
        }
        if ("VEC2".equals(type)) {
            return 2;
        }
        if ("VEC3".equals(type)) {
            return 3;
        }
        if ("VEC4".equals(type)) {
            return 4;
        }
        if ("MAT4".equals(type)) {
            return 16;
        }
        throw new IllegalArgumentException("accessor type " + type);
    }

    private static int componentSize(int componentType) {
        switch (componentType) {
            case 5120:
            case 5121:
                return 1;
            case 5122:
            case 5123:
                return 2;
            default:
                return 4;
        }
    }

    /** Reads an accessor as doubles (normalized integer types scaled to 0..1 / -1..1). */
    private double[] read(int index) throws IOException {
        Map<String, Object> acc = accessor(index);
        int count = (int) Json.integer(acc, "count", 0);
        int comps = components(Json.string(acc, "type"));
        int ctype = (int) Json.integer(acc, "componentType", 5126);
        boolean normalized = Json.bool(acc, "normalized");
        double[] out = new double[count * comps];
        if (!(acc.get("bufferView") instanceof Number)) {
            return out; // all zeros (sparse accessors without a view)
        }
        Map<String, Object> bv = asMap(Json.array(doc, "bufferViews").get(((Number) acc.get("bufferView")).intValue()));
        byte[] buf = buffers.get((int) Json.integer(bv, "buffer", 0));
        int base = (int) Json.integer(bv, "byteOffset", 0) + (int) Json.integer(acc, "byteOffset", 0);
        int csize = componentSize(ctype);
        int stride = (int) Json.integer(bv, "byteStride", 0);
        if (stride == 0) {
            stride = csize * comps;
        }
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int p = base + i * stride;
            for (int c = 0; c < comps; c++) {
                int q = p + c * csize;
                double v;
                switch (ctype) {
                    case 5120:
                        v = bb.get(q);
                        if (normalized) {
                            v = Math.max(v / 127.0, -1);
                        }
                        break;
                    case 5121:
                        v = bb.get(q) & 0xff;
                        if (normalized) {
                            v /= 255.0;
                        }
                        break;
                    case 5122:
                        v = bb.getShort(q);
                        if (normalized) {
                            v = Math.max(v / 32767.0, -1);
                        }
                        break;
                    case 5123:
                        v = bb.getShort(q) & 0xffff;
                        if (normalized) {
                            v /= 65535.0;
                        }
                        break;
                    case 5125:
                        v = bb.getInt(q) & 0xffffffffL;
                        break;
                    default:
                        v = bb.getFloat(q);
                }
                out[i * comps + c] = v;
            }
        }
        return out;
    }

    private float[] floats(int accessor) throws IOException {
        double[] d = read(accessor);
        float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            f[i] = (float) d[i];
        }
        return f;
    }

    private int[] ints(int accessor) throws IOException {
        double[] d = read(accessor);
        int[] out = new int[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (int) d[i];
        }
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<String, Object>();
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    private static Vector3 vec(List<Object> l) {
        return l.size() >= 3 ? new Vector3(num(l.get(0)), num(l.get(1)), num(l.get(2))) : Vector3.ZERO;
    }

    private static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
