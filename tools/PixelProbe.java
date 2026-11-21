import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** 对比两张截图，报告差异区域与采样点像素。 */
public final class PixelProbe {
    public static void main(String[] args) throws Exception {
        BufferedImage a = ImageIO.read(new File(args[0]));
        BufferedImage b = ImageIO.read(new File(args[1]));
        System.out.println("A=" + a.getWidth() + "x" + a.getHeight()
                + "  B=" + b.getWidth() + "x" + b.getHeight());

        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        int minX = w, minY = h, maxX = -1, maxY = -1, diffCount = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diffCount++;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        System.out.println("差异像素(采样)=" + diffCount);
        if (maxX >= 0) {
            System.out.println("差异范围 x[" + minX + "," + maxX + "] y[" + minY + "," + maxY + "]");
        }
        int[][] pts = {{540, 300}, {540, 700}, {200, 1400}, {900, 1400},
                {540, 1900}, {540, 2200}, {60, 200}};
        for (int[] p : pts) {
            if (p[0] < w && p[1] < h) {
                System.out.printf("  (%d,%d) A=%08X B=%08X%n", p[0], p[1],
                        a.getRGB(p[0], p[1]), b.getRGB(p[0], p[1]));
            }
        }
        // 统计棋盘区域平均亮度
        System.out.printf("  棋盘区域均值 A=%.1f B=%.1f%n",
                meanLum(a, 120, 600, 960, 1700), meanLum(b, 120, 600, 960, 1700));
    }

    private static double meanLum(BufferedImage img, int x0, int y0, int x1, int y1) {
        double sum = 0;
        int n = 0;
        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
                sum += 0.299 * r + 0.587 * g + 0.114 * bl;
                n++;
            }
        }
        return sum / n;
    }
}
