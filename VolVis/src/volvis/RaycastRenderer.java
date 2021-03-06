/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;
import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Timer timer;
    public boolean highRes = false;

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;

    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
        this.timer = new Timer();
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());

        // uncomment this to initialize the TF with good starting values for the orange dataset 
        tFunc.setTestFunc();
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());

        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }

    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }

    short getVoxel(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }

    double getGradientMagnitude(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() - 1 || coord[1] < 0 || coord[1] > volume.getDimY() - 1
                || coord[2] < 0 || coord[2] > volume.getDimZ() - 1) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return gradients.getGradient(x, y, z).mag;
    }
    
    double getGradientMagnitudeInterpolated(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() - 1 || coord[1] < 0 || coord[1] > volume.getDimY() - 1
                || coord[2] < 0 || coord[2] > volume.getDimZ() - 1) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        if (x > volume.getDimX() - 2
                || y > volume.getDimY() - 2
                || z > volume.getDimZ() - 2) {
            return 0;
        }

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);

        // Math.min used to prevent ArrayIndexOutOfBoundsException
        int x1 = (int) Math.min(Math.ceil(x), volume.getDimX() - 1);
        int y1 = (int) Math.min(Math.ceil(y), volume.getDimY() - 1);
        int z1 = (int) Math.min(Math.ceil(z), volume.getDimZ() - 1);

        double alpha = x - x0;
        double beta = y - y0;
        double gamma = z - z0;

        short v = (short) ((1 - alpha) * (1 - beta) * (1 - gamma) * gradients.getGradient(x0, y0, z0).mag
                + alpha * (1 - beta) * (1 - gamma) * gradients.getGradient(x1, y0, z0).mag
                + (1 - alpha) * beta * (1 - gamma) * gradients.getGradient(x0, y1, z0).mag
                + alpha * beta * (1 - gamma) * gradients.getGradient(x1, y1, z0).mag
                + (1 - alpha) * (1 - beta) * gamma * gradients.getGradient(x0, y0, z1).mag
                + alpha * (1 - beta) * gamma * gradients.getGradient(x1, y0, z1).mag
                + (1 - alpha) * beta * gamma * gradients.getGradient(x0, y1, z1).mag
                + alpha * beta * gamma * gradients.getGradient(x1, y1, z1).mag);

        return v;
    }

    double getAlpha(double[] coord) {
        TFColor color = this.tfEditor2D.triangleWidget.color;        
        double baseIntensity = this.tfEditor2D.triangleWidget.baseIntensity;
        double radius = this.tfEditor2D.triangleWidget.radius;
        double minGradient = this.tfEditor2D.triangleWidget.minGradient;
        double maxGradient = this.tfEditor2D.triangleWidget.maxGradient;
        double baseGradient = this.tfEditor2D.triangleWidget.baseGradient;
        double gradientMagnitude = this.getGradientMagnitudeInterpolated(coord) - baseGradient;
        double voxelIntensity = this.getVoxelInterpolated(coord);

        if (gradientMagnitude < minGradient || gradientMagnitude > maxGradient) {
            return 0;
        }
        if (gradientMagnitude == 0 && voxelIntensity == baseIntensity) {
            return color.a;
        }
        if (gradientMagnitude > 0
                && voxelIntensity - radius * gradientMagnitude <= baseIntensity
                && baseIntensity <= voxelIntensity + radius * gradientMagnitude) {
            return color.a * (1 - (1 / radius) * Math.abs((baseIntensity - voxelIntensity) / gradientMagnitude));
        }
        return 0;
    }

    short getVoxelInterpolated(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        double x = coord[0];
        double y = coord[1];
        double z = coord[2];

        if (x > volume.getDimX() - 2
                || y > volume.getDimY() - 2
                || z > volume.getDimZ() - 2) {
            return 0;
        }

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);

        // Math.min used to prevent ArrayIndexOutOfBoundsException
        int x1 = (int) Math.min(Math.ceil(x), volume.getDimX() - 1);
        int y1 = (int) Math.min(Math.ceil(y), volume.getDimY() - 1);
        int z1 = (int) Math.min(Math.ceil(z), volume.getDimZ() - 1);

        double alpha = x - x0;
        double beta = y - y0;
        double gamma = z - z0;

        short v = (short) ((1 - alpha) * (1 - beta) * (1 - gamma) * volume.getVoxel(x0, y0, z0)
                + alpha * (1 - beta) * (1 - gamma) * volume.getVoxel(x1, y0, z0)
                + (1 - alpha) * beta * (1 - gamma) * volume.getVoxel(x0, y1, z0)
                + alpha * beta * (1 - gamma) * volume.getVoxel(x1, y1, z0)
                + (1 - alpha) * (1 - beta) * gamma * volume.getVoxel(x0, y0, z1)
                + alpha * (1 - beta) * gamma * volume.getVoxel(x1, y0, z1)
                + (1 - alpha) * beta * gamma * volume.getVoxel(x0, y1, z1)
                + alpha * beta * gamma * volume.getVoxel(x1, y1, z1));

        return v;
    }

    void slicer(double[] viewMatrix) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord);

                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                // voxelColor = tFunc.getColor(val);

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }

    void mip(double[] viewMatrix, boolean highRes) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        // higher resolution leads to lower quality but better performance 
        int resolution; // the number to the power of 2 of pixels treated as one
        if (highRes) {
            resolution = 1;
        } else {
            resolution = 3;
        }
        double rayResolution = 1; // stepsize of t in raycast

        // calculate size of ray
        double raySize = Math.sqrt(Math.pow(volume.getDimX(), 2)
                + Math.pow(volume.getDimY(), 2)
                + Math.pow(volume.getDimZ(), 2));
                
        for (int j = 0; j < image.getHeight() - resolution + 1; j += resolution) {
            for (int i = 0; i < image.getWidth() - resolution + 1; i += resolution) {                
                // find maximum value along array
                int rayMax = 0;
                for (double t = -0.5 * raySize; t < 0.5 * raySize; t += rayResolution) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + viewVec[0] * t + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + viewVec[1] * t + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + viewVec[2] * t + volumeCenter[2];

                    int val = getVoxelInterpolated(pixelCoord);
                    if (val > rayMax) {
                        rayMax = val;
                    }

                    //voxelColor = tFunc.getColor(val);
                }

                // Map the intensity to a grey value by linear scaling
                voxelColor.r = rayMax / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = rayMax > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                //TransferFunction;
                //TransferFunction kleurtjes = new TransferFunction((short)1.0,(short)10);
                //int pixelColor = (int) kleurtjes.getColor((int)rayMax);

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;

                // make multiple pixels the same color for the sake of lower resolution
                if (resolution == 1) {
                    image.setRGB(i, j, pixelColor);
                } else if (resolution == 3) {
                    for (int jPixel = -1; jPixel < 2; jPixel++) {
                        for (int iPixel = -1; iPixel < 2; iPixel++) {
                            if (i + iPixel >= 0 && i + iPixel < image.getWidth()
                                    && j + jPixel >= 0 && j + jPixel < image.getHeight()) {
                                image.setRGB(i + iPixel, j + jPixel, pixelColor);
                            }
                        }
                    }
                }
            }
        }
    }

    void compositing(double[] viewMatrix, boolean highRes) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        TFColor color = new TFColor();
        TFColor voxelColor = new TFColor();

        // higher resolution leads to lower quality but better performance 
        int resolution; // the number to the power of 2 of pixels treated as one
        if (highRes) {
            resolution = 1;
        } else {
            resolution = 3;
        }
        double rayResolution = 1; // stepsize of t in raycast

        // calculate size of ray
        double raySize = Math.sqrt(Math.pow(volume.getDimX(), 2)
                + Math.pow(volume.getDimY(), 2)
                + Math.pow(volume.getDimZ(), 2));
                
        for (int j = 0; j < image.getHeight() - resolution + 1; j += resolution) {
            for (int i = 0; i < image.getWidth() - resolution + 1; i += resolution) {                
                color.r = color.g = color.b = 0.0;
                color.a = 1.0;
                for (double t = -0.5 * raySize; t < 0.5 * raySize; t += rayResolution) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + viewVec[0] * t + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + viewVec[1] * t + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + viewVec[2] * t + volumeCenter[2];

                    int val = getVoxelInterpolated(pixelCoord);
                    voxelColor = tFunc.getColor(val);
                    color.r = voxelColor.a * voxelColor.r + (1 - voxelColor.a) * color.r;
                    color.g = voxelColor.a * voxelColor.g + (1 - voxelColor.a) * color.g;
                    color.b = voxelColor.a * voxelColor.b + (1 - voxelColor.a) * color.b;
                }

                // Make transparent if no color
//                if (color.r + color.g + color.b == 0.0) {
//                    color.a = 0.0;
//                }

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = color.a <= 1.0 ? (int) Math.floor(color.a * 255) : 255;
                int c_red = color.r <= 1.0 ? (int) Math.floor(color.r * 255) : 255;
                int c_green = color.g <= 1.0 ? (int) Math.floor(color.g * 255) : 255;
                int c_blue = color.b <= 1.0 ? (int) Math.floor(color.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;

                // make multiple pixels the same color for the sake of lower resolution
                if (resolution == 1) {
                    image.setRGB(i, j, pixelColor);
                } else if (resolution == 3) {
                    for (int jPixel = -1; jPixel < 2; jPixel++) {
                        for (int iPixel = -1; iPixel < 2; iPixel++) {
                            if (i + iPixel >= 0 && i + iPixel < image.getWidth()
                                    && j + jPixel >= 0 && j + jPixel < image.getHeight()) {
                                image.setRGB(i + iPixel, j + jPixel, pixelColor);
                            }
                        }
                    }
                }
            }
        }
    }

    void gradient(double[] viewMatrix, boolean highRes) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        TFColor color = new TFColor();
        TFColor voxelColor = new TFColor();

        // higher resolution leads to lower quality but better performance 
        int resolution; // the number to the power of 2 of pixels treated as one
        if (highRes) {
            resolution = 1;
        } else {
            resolution = 3;
        }
        double rayResolution = 1; // stepsize of t in raycast

        // calculate size of ray
        double raySize = Math.sqrt(Math.pow(volume.getDimX(), 2)
                + Math.pow(volume.getDimY(), 2)
                + Math.pow(volume.getDimZ(), 2));
                
        for (int j = 0; j < image.getHeight() - resolution + 1; j += resolution) {
            for (int i = 0; i < image.getWidth() - resolution + 1; i += resolution) {                
                color.r = color.g = color.b = 0.0;
                color.a = 1.0;
                for (double t = -0.5 * raySize; t < 0.5 * raySize; t += rayResolution) {
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + viewVec[0] * t + volumeCenter[0];
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + viewVec[1] * t + volumeCenter[1];
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + viewVec[2] * t + volumeCenter[2];

                    voxelColor = this.tfEditor2D.triangleWidget.color;
                    double alpha = this.getAlpha(pixelCoord);
                    
                    double shade;
                    if (panel.shade) {
                        shade = getShade(viewMatrix, pixelCoord);
                    } else {
                        shade = 1.0;
                    }
                
                    color.r = alpha * voxelColor.r * shade + (1 - alpha) * color.r;
                    color.g = alpha * voxelColor.g * shade + (1 - alpha) * color.g;
                    color.b = alpha * voxelColor.b * shade + (1 - alpha) * color.b;
                }

                // Make transparent if no color
                if (color.r + color.g + color.b == 0.0) {
                    color.a = 0.0;
                }

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = color.a <= 1.0 ? (int) Math.floor(color.a * 255) : 255;
                int c_red = color.r <= 1.0 ? (int) Math.floor(color.r * 255) : 255;
                int c_green = color.g <= 1.0 ? (int) Math.floor(color.g * 255) : 255;
                int c_blue = color.b <= 1.0 ? (int) Math.floor(color.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;

                // make multiple pixels the same color for the sake of lower resolution
                if (resolution == 1) {
                    image.setRGB(i, j, pixelColor);
                } else if (resolution == 3) {
                    for (int jPixel = -1; jPixel < 2; jPixel++) {
                        for (int iPixel = -1; iPixel < 2; iPixel++) {
                            if (i + iPixel >= 0 && i + iPixel < image.getWidth()
                                    && j + jPixel >= 0 && j + jPixel < image.getHeight()) {
                                image.setRGB(i + iPixel, j + jPixel, pixelColor);
                            }
                        }
                    }
                }
            }
        }
    }

    public double getShade(double[] viewMatrix, double[] coord) {
        if (coord[0] < 0 || coord[0] > volume.getDimX() - 1|| coord[1] < 0 || coord[1] > volume.getDimY() - 1
                || coord[2] < 0 || coord[2] > volume.getDimZ() - 1) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        double[] N = new double[3];
        VoxelGradient gradient = gradients.getGradient(x, y, z);
        VectorMath.setVector(N, -gradient.x / gradient.mag, -gradient.y / gradient.mag, -gradient.z / gradient.mag);

        double[] V = new double[3];
        VectorMath.setVector(V, -viewMatrix[2], -viewMatrix[6], -viewMatrix[10]);
        double VLength = VectorMath.length(V);

        double[] L = new double[3];
        VectorMath.setVector(L, V[0] / VLength, V[1] / VLength, V[2] / VLength);

        double[] H = new double[3];
        VectorMath.setVector(H, V[0] / VLength, V[1] / VLength, V[2] / VLength);

        double iAmb = 1;
        double kAmb = 0.1;
        double iDiff = 1;
        double kDiff = 0.7;
        double kSpec = 0.2;
        double alpha = 10;

        double shade = 0;
        double ambTerm = iAmb * kAmb;
        if (ambTerm > 0)
            shade += ambTerm;
        double diffTerm = iDiff * kDiff * (VectorMath.dotproduct(N, L));
        if (diffTerm > 0)
            shade += diffTerm;        
        double specTerm = kSpec * Math.pow(VectorMath.dotproduct(N, H), alpha);
        if (specTerm > 0)
            shade += specTerm;
        
        return shade;
    }

    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }

    @Override
    public void visualize(GL2 gl) {

        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();

        RenderType renderType = panel.renderType;
        switch (renderType) {
            case SLICER:
                slicer(viewMatrix);
                break;
            case MIP:
                mip(viewMatrix, this.highRes);
                break;
            case COMPOSITING:
                compositing(viewMatrix, this.highRes);
                break;
            case TFUNC_2D:
                gradient(viewMatrix, this.highRes);
                break;
        }

        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();

        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

        if (!this.highRes && (renderType == RenderType.MIP || renderType == RenderType.COMPOSITING || renderType == RenderType.TFUNC_2D)) {
            this.timer.cancel();
            this.timer = new Timer();
            timer.schedule(new RevisualizeTask(this), 1000);
        }
        this.highRes = false;

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    @Override
    public void changed() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}

class RevisualizeTask extends TimerTask {

    RaycastRenderer invoker;

    RevisualizeTask(RaycastRenderer invoker) {
        this.invoker = invoker;
    }

    @Override
    public void run() {
        invoker.highRes = true;
        invoker.changed();
    }
}
