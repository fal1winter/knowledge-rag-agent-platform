package com.liang.bbs.user.service.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class GenerateUtils {

    public static byte[] generateFileName(String hash) {
        // 你原来的酷炫渐变版本
        return generateCyberPixelAvatar(hash);
    }

    /**
     * 渐变酷炫几何风
     */
    public static byte[] generateGradientAvatar(String hash) {
        try {
            long seed = hashToLong(hash);
            Random random = new Random(seed);

            int size = 256;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 渐变背景
            Color color1 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            Color color2 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            g.setPaint(new GradientPaint(0, 0, color1, size, size, color2));
            g.fillRect(0, 0, size, size);

            // 随机几何块
            for (int i = 0; i < 8; i++) {
                g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 150));
                int w = random.nextInt(size / 2) + 30;
                int h = random.nextInt(size / 2) + 30;
                int x = random.nextInt(size - w);
                int y = random.nextInt(size - h);
                g.rotate(Math.toRadians(random.nextInt(360)), x + w / 2.0, y + h / 2.0);
                g.fillRoundRect(x, y, w, h, random.nextInt(50), random.nextInt(50));
                g.setTransform(new AffineTransform());
            }

            // 光晕
            g.setPaint(new RadialGradientPaint(size / 2f, size / 2f, size / 1.5f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 120), new Color(255, 255, 255, 0)}));
            g.fillOval(0, 0, size, size);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("头像生成失败", e);
        }
    }

    public static byte[] generateCyberPixelAvatar(String hash) {
        try {
            long seed = hashToLong(hash);
            Random random = new Random(seed);

            int gridSize = 7;       // 7x7 像素格
            int pixelSize = 40;     // 每个像素块大小
            int size = gridSize * pixelSize;

            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景渐变
            Color bg1 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            Color bg2 = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            g.setPaint(new GradientPaint(0, 0, bg1, size, size, bg2));
            g.fillRect(0, 0, size, size);

            // 主色调（高亮）
            Color mainColor = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            Color lighterMain = mainColor.brighter();

            // 生成对称像素块
            for (int x = 0; x < (gridSize + 1) / 2; x++) {
                for (int y = 0; y < gridSize; y++) {
                    if (random.nextBoolean()) {
                        // 随机用主色调或亮色调
                        g.setColor(random.nextBoolean() ? mainColor : lighterMain);
                        g.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
                        // 对称
                        g.fillRect((gridSize - 1 - x) * pixelSize, y * pixelSize, pixelSize, pixelSize);
                    }
                }
            }

            // 添加像素光效（外圈发光）
            g.setPaint(new RadialGradientPaint(
                    size / 2f, size / 2f, size / 1.2f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 80), new Color(255, 255, 255, 0)}
            ));
            g.fillOval(0, 0, size, size);

            // 添加内发光边框
            g.setStroke(new BasicStroke(4));
            g.setColor(new Color(255, 255, 255, 120));
            g.drawRect(2, 2, size - 4, size - 4);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("赛博像素头像生成失败", e);
        }
    }

    private static long hashToLong(String hash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(hash.getBytes());
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (digest[i] & 0xff);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
