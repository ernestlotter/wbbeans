package org.webbeans.workbench.viewer3d;

import org.webbeans.scene3d.Geometry;

/** The turbine's shapes: primitives for hub, shaft, housing and sensors; a programmatically built, twisted blade. */
final class Geometries {

    static final Geometry HUB = Geometry.cylinder(0.8, 0.8, 0.6, 32);
    static final Geometry SHAFT = Geometry.cylinder(0.25, 0.25, 4, 24);
    static final Geometry HOUSING = Geometry.torus(3.2, 0.25, 12, 64);
    static final Geometry SENSOR = Geometry.box(0.3, 0.3, 0.3);
    static final Geometry BLADE = blade(0.8, 2.6, 0.55, 0.22, 0.6, 14);

    private Geometries() {
    }

    /**
     * A blade along +Y from {@code r0} to {@code r1}: a strip of {@code segments} quads whose chord tapers from
     * {@code chord0} to {@code chord1} and twists by {@code twist} radians, as an indexed triangle mesh with
     * client-computed normals — the {@code Geometry.buffer()} path, uploaded once as a blob.
     */
    static Geometry blade(double r0, double r1, double chord0, double chord1, double twist, int segments) {
        int rows = segments + 1;
        float[] pos = new float[rows * 2 * 3];
        float[] uv = new float[rows * 2 * 2];
        int[] idx = new int[segments * 6];
        for (int i = 0; i < rows; i++) {
            double t = (double) i / segments;
            double r = r0 + (r1 - r0) * t;
            double chord = chord0 + (chord1 - chord0) * t;
            double a = twist * t;
            double cx = Math.cos(a) * chord / 2, cz = Math.sin(a) * chord / 2;
            pos[i * 6] = (float) -cx;
            pos[i * 6 + 1] = (float) r;
            pos[i * 6 + 2] = (float) -cz;
            pos[i * 6 + 3] = (float) cx;
            pos[i * 6 + 4] = (float) r;
            pos[i * 6 + 5] = (float) cz;
            uv[i * 4] = 0;
            uv[i * 4 + 1] = (float) t;
            uv[i * 4 + 2] = 1;
            uv[i * 4 + 3] = (float) t;
        }
        for (int i = 0; i < segments; i++) {
            int a = i * 2, b = a + 1, c = a + 2, d = a + 3;
            idx[i * 6] = a;
            idx[i * 6 + 1] = c;
            idx[i * 6 + 2] = b;
            idx[i * 6 + 3] = b;
            idx[i * 6 + 4] = c;
            idx[i * 6 + 5] = d;
        }
        return Geometry.buffer().positions(pos).uvs(uv).indices(idx).computeNormals(true).build();
    }
}
