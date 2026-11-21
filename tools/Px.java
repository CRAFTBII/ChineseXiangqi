import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Px {
    public static void main(String[] a) throws Exception {
        BufferedImage img = ImageIO.read(new File(a[0]));
        System.out.println("size " + img.getWidth() + "x" + img.getHeight());
        int y = Integer.parseInt(a[1]);
        StringBuilder sb = new StringBuilder("y=" + y + " : ");
        for (int x = 40; x < img.getWidth(); x += 80) {
            int p = img.getRGB(x, y) & 0xFFFFFF;
            sb.append(String.format("%d=#%06X ", x, p));
        }
        System.out.println(sb);
    }
}
