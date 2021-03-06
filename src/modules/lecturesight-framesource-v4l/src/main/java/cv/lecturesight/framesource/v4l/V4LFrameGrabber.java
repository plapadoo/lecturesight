/* Copyright (C) 2012 Benjamin Wulff
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package cv.lecturesight.framesource.v4l;

import au.edu.jcu.v4l4j.CaptureCallback;
import au.edu.jcu.v4l4j.FrameGrabber;
import au.edu.jcu.v4l4j.ImageFormat;
import au.edu.jcu.v4l4j.ResolutionInfo;
import au.edu.jcu.v4l4j.VideoDevice;
import au.edu.jcu.v4l4j.VideoFrame;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;
import cv.lecturesight.framesource.FrameGrabber.PixelFormat;
import cv.lecturesight.framesource.FrameSourceException;
import cv.lecturesight.util.Log;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Vector;

public class V4LFrameGrabber implements cv.lecturesight.framesource.FrameGrabber, CaptureCallback {

  private Log log;
  private int exceptionCount = 0;
  private final int MAX_EXCEPTIONS = 5;
  VideoDevice device;
  int width, height, standard, channel, quality;
  private FrameGrabber grabber;
  private ByteBuffer frameBuffer;

  V4LFrameGrabber(VideoDevice device, int frameWidth, int frameHeight, int videoStandard,
          int videoChannel, int videoQuality) throws FrameSourceException {
    this.device = device;
    this.width = frameWidth;
    this.height = frameHeight;
    this.standard = videoStandard;
    this.channel = videoChannel;
    this.quality = videoQuality;
    Vector<ImageFormat> useFormat = new Vector<ImageFormat>();
    try {
      log = new Log(device.getDeviceInfo().getName());
      List<ImageFormat> imageFormats = device.getDeviceInfo().getFormatList().getNativeFormats();
      for (ImageFormat imageFormat : imageFormats) {
        log.info("supported Format: " + imageFormat.getName());
        ResolutionInfo resolutions = imageFormat.getResolutionInfo();
        for (ResolutionInfo.DiscreteResolution disRes : resolutions.getDiscreteResolutions()) {
          if (disRes.getHeight() == frameHeight && disRes.getWidth() == frameWidth) {
            useFormat.add(imageFormat);
          }
        }
      }
      log.info("using : " + useFormat.firstElement().getName());
      grabber = device.getRGBFrameGrabber(width, height, channel, standard, useFormat.firstElement());
      frameBuffer = ByteBuffer.allocate(grabber.getWidth() * grabber.getHeight() * 3);
      grabber.setCaptureCallback(this);
      grabber.startCapture();
    } catch (V4L4JException e) {
      throw new FrameSourceException("Failed to initialize FrameGrabber on device " + device.getDevicefile());
    }
  }

  @Override
  public int getWidth() {
    return width;
  }

  @Override
  public int getHeight() {
    return height;
  }

  @Override
  public PixelFormat getPixelFormat() {
    return PixelFormat.RGB_8BIT;
  }

  @Override
  public void nextFrame(VideoFrame frame) {
    exceptionCount = 0;
    frameBuffer = ByteBuffer.wrap(frame.getBytes());
    frame.recycle();
  }

  @Override
  public Buffer captureFrame() throws FrameSourceException {
    if (frameBuffer != null) {
      return frameBuffer;
    } else {
      throw new FrameSourceException("Could not capture frame from " + device.getDevicefile());
    }
  }

  @Override
  public void exceptionReceived(V4L4JException vlje) {
    exceptionCount++;
    log.error("Could not capture frame from " + device.getDevicefile() + ": ", vlje);
    if (exceptionCount < MAX_EXCEPTIONS) {
      log.info("Trying to restart frame grabber on " + device.getDevicefile() + ".");
      try {
        grabber.startCapture();
      } catch (V4L4JException ex) {
        log.error("Could restart not frame grabber on" + device.getDevicefile() + ": ", ex);
      }
    } else {
      // Hopeless
      log.info("Frame grabber failed on " + device.getDevicefile() + Integer.toString(MAX_EXCEPTIONS) + " times ... giving up.");
      shutdown();
    }
  }

  void shutdown() {
    log.info("Shutting down");
    try {
      grabber.stopCapture();
      device.releaseFrameGrabber();
      device.release();
    } catch (Exception e) {
      log.error("Error during shutdown. ", e);
    } finally {
      // TODO tell consuming FrameSource to deactivate
    }
  }

  @Override
  public void finalize() throws Throwable {
    super.finalize();
    shutdown();
  }
}
