package com.ellasgame.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public final class RegionSelectionPanel extends JPanel {
    private final BufferedImage image;
    private Rectangle imageBounds = new Rectangle();
    private Rectangle selectedRegion = new Rectangle();
    private Point dragStart;

    public RegionSelectionPanel(BufferedImage image) {
        this.image = image;
        setBackground(new Color(12, 16, 22));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!imageBounds.contains(event.getPoint())) {
                    return;
                }
                dragStart = clampToImage(event.getPoint());
                selectedRegion = new Rectangle(dragStart);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart == null) {
                    return;
                }
                Point dragEnd = clampToImage(event.getPoint());
                selectedRegion = rectangleBetween(dragStart, dragEnd);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragStart = null;
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    public Rectangle selectedRegionInImageCoordinates() {
        if (selectedRegion.isEmpty() || imageBounds.isEmpty()) {
            return new Rectangle();
        }

        double xScale = (double) image.getWidth() / imageBounds.width;
        double yScale = (double) image.getHeight() / imageBounds.height;
        return new Rectangle(
                (int) Math.round((selectedRegion.x - imageBounds.x) * xScale),
                (int) Math.round((selectedRegion.y - imageBounds.y) * yScale),
                (int) Math.round(selectedRegion.width * xScale),
                (int) Math.round(selectedRegion.height * yScale));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        paintImage(graphics2D);
        paintSelection(graphics2D);
        graphics2D.dispose();
    }

    private void paintImage(Graphics2D graphics) {
        double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        int x = (getWidth() - width) / 2;
        int y = (getHeight() - height) / 2;
        imageBounds = new Rectangle(x, y, width, height);
        graphics.drawImage(image, x, y, width, height, null);
    }

    private void paintSelection(Graphics2D graphics) {
        if (selectedRegion.isEmpty()) {
            return;
        }

        graphics.setColor(new Color(20, 24, 30, 125));
        graphics.fillRect(imageBounds.x, imageBounds.y, imageBounds.width, selectedRegion.y - imageBounds.y);
        graphics.fillRect(
                imageBounds.x,
                selectedRegion.y + selectedRegion.height,
                imageBounds.width,
                imageBounds.y + imageBounds.height - selectedRegion.y - selectedRegion.height);
        graphics.fillRect(imageBounds.x, selectedRegion.y, selectedRegion.x - imageBounds.x, selectedRegion.height);
        graphics.fillRect(
                selectedRegion.x + selectedRegion.width,
                selectedRegion.y,
                imageBounds.x + imageBounds.width - selectedRegion.x - selectedRegion.width,
                selectedRegion.height);

        graphics.setColor(new Color(242, 194, 90));
        graphics.setStroke(new BasicStroke(3f));
        graphics.draw(selectedRegion);
    }

    private Point clampToImage(Point point) {
        int x = Math.max(imageBounds.x, Math.min(point.x, imageBounds.x + imageBounds.width));
        int y = Math.max(imageBounds.y, Math.min(point.y, imageBounds.y + imageBounds.height));
        return new Point(x, y);
    }

    private static Rectangle rectangleBetween(Point start, Point end) {
        int x = Math.min(start.x, end.x);
        int y = Math.min(start.y, end.y);
        int width = Math.abs(start.x - end.x);
        int height = Math.abs(start.y - end.y);
        return new Rectangle(x, y, width, height);
    }
}
