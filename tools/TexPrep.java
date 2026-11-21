import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

/**
 * 把 ambientCG 的 CC0 木纹原图做成游戏用贴图：
 * 灰度 → 渐变映射到目标色板 → 缩放 → 输出 JPEG。
 * 另生成一张"同心圆年轮"棋子面贴图（极坐标采样）。
 */
public final class TexPrep {

    public static void main(String[] args) throws Exception {
        String src = args[0];
        String outDir = args[1];
        new File(outDir).mkdirs();

        BufferedImage pine = ImageIO.read(new File(src, "Wood096/Wood096_1K-JPG_Color.jpg"));
        BufferedImage honey = ImageIO.read(new File(src, "Wood092/Wood092_1K-JPG_Color.jpg"));
        BufferedImage walnut = ImageIO.read(new File(src, "Wood051/Wood051_1K-JPG_Color.jpg"));

        // 棋盘面：浅松木，低对比
        int[][] boardRamp = {
                {0, 0xB08A55}, {90, 0xC8A470}, {150, 0xDDC08F},
                {205, 0xEEDBB4}, {255, 0xF9EDD3}
        };
        save(gradientMap(pine, boardRamp, 0.60f, 0.66f), new File(outDir, "tex_board.jpg"), 0.92f);

        // 外框 / 桌面：深胡桃
        int[][] frameRamp = {
                {0, 0x4A2E14}, {70, 0x5E3D1D}, {130, 0x765026},
                {190, 0x8C6034}, {255, 0xA2764A}
        };
        save(gradientMap(walnut, frameRamp, 0.50f, 0.62f), new File(outDir, "tex_frame.jpg"), 0.90f);

        // 桌面背景：更深一档
        int[][] tableRamp = {
                {0, 0x241405}, {80, 0x35200D}, {150, 0x472C15},
                {210, 0x593A1E}, {255, 0x6B4828}
        };
        save(gradientMap(walnut, tableRamp, 0.44f, 0.58f), new File(outDir, "tex_table.jpg"), 0.88f);

        // 棋子面：暖蜜色 + 同心年轮
        int[][] pieceRamp = {
                {0, 0xC69C64}, {80, 0xD8B47E}, {150, 0xE8CC9C},
                {210, 0xF5E1BE}, {255, 0xFDF3DE}
        };
        BufferedImage piece = radialPiece(honey, pieceRamp);
        save(piece, new File(outDir, "tex_piece.jpg"), 0.94f);

        System.out.println("textures written");
    }

    private static BufferedImage gradientMap(BufferedImage src, int[][] ramp,
                                             float center, float gain) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
                float t = center + (lum - 0.5f) * gain;
                t = Math.max(0f, Math.min(1f, t));
                row[x] = sampleRamp(ramp, t);
            }
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        return downscale(out, 768, 768);
    }

    private static int sampleRamp(int[][] ramp, float t) {
        int v = Math.round(t * 255f);
        for (int i = 0; i < ramp.length - 1; i++) {
            int lo = ramp[i][0];
            int hi = ramp[i + 1][0];
            if (v <= hi) {
                float k = (v - lo) / (float) Math.max(1, hi - lo);
                k = Math.max(0f, Math.min(1f, k));
                return blend(ramp[i][1], ramp[i + 1][1], k);
            }
        }
        return ramp[ramp.length - 1][1];
    }

    private static int blend(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** 车削年轮：程序化生成（避免极坐标采样在 0°/360° 处出现接缝）。 */
    private static BufferedImage radialPiece(BufferedImage src, int[][] ramp) {
        int size = 256;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        float half = size / 2f;
        // 偏心髓心，年轮才像真实车削件
        float px0 = 0.07f;
        float py0 = -0.05f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - half) / half;
                float dy = (y + 0.5f - half) / half;
                float r = (float) Math.sqrt((dx - px0) * (dx - px0) + (dy - py0) * (dy - py0));
                float a = (float) Math.atan2(dy, dx);

                float t = 0.635f;
                // 年轮：疏密随半径略加密
                float ring = r * 7.4f + 0.11f * (float) Math.sin(a * 2.3f + 0.7f);
                t += 0.030f * (float) Math.sin(ring * 2f * Math.PI);
                // 木纤维纹理
                t += 0.034f * noise(x * 0.055f, y * 0.055f, 11);
                t += 0.015f * noise(x * 0.21f, y * 0.21f, 37);
                // 边缘微暗
                float rr = (float) Math.sqrt(dx * dx + dy * dy);
                t -= rr * rr * 0.075f;
                t = Math.max(0f, Math.min(1f, t));
                out.setRGB(x, y, sampleRamp(ramp, t));
            }
        }
        return out;
    }

    /** 平滑值噪声，确定性哈希，无需额外依赖。 */
    private static float noise(float x, float y, int seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float u = fx * fx * (3f - 2f * fx);
        float v = fy * fy * (3f - 2f * fy);
        float a = hash(ix, iy, seed);
        float b = hash(ix + 1, iy, seed);
        float c = hash(ix, iy + 1, seed);
        float d = hash(ix + 1, iy + 1, seed);
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 69069;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return ((h & 0xFFFF) / 32768f) - 1f;
    }

    private static BufferedImage downscale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void save(BufferedImage img, File file, float quality) throws Exception {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = it.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();
        System.out.println("  " + file.getName() + " " + file.length() / 1024 + " KB");
    }
}
