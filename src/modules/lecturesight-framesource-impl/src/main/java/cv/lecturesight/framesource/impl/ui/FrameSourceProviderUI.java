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
package cv.lecturesight.framesource.impl.ui;

import cv.lecturesight.display.DisplayService;
import cv.lecturesight.framesource.FrameSourceProvider;
import cv.lecturesight.gui.api.UserInterface;
import javax.swing.JPanel;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

@Component(name = "lecturesight.framesource.ui", immediate = true)
@Service
@Properties({
  @Property(name = "lecturesight.gui.title", value = "Input")
})
public class FrameSourceProviderUI implements UserInterface {
  
  @Reference
  FrameSourceProvider fsp;
  @Reference
  DisplayService dsps;
  
  FrameSourceProviderUIPanel ui;
  
  protected void activate(ComponentContext cc) {
    ui = new FrameSourceProviderUIPanel(dsps.getDisplayBySID("input"));
  }
  
  @Override
  public JPanel getPanel() {
    return ui;
  }
  
  @Override
  public String getTitle() {
    return "Frame Source";
  }

  @Override
  public boolean isResizeable() {
    return false;
  }
}
