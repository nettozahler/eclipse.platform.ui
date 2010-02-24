package org.eclipse.e4.workbench.ui.renderers.swt.dnd;

import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.MApplicationFactory;
import org.eclipse.e4.ui.model.application.MElementContainer;
import org.eclipse.e4.ui.model.application.MPSCElement;
import org.eclipse.e4.ui.model.application.MToolBar;
import org.eclipse.e4.ui.model.application.MToolItem;
import org.eclipse.e4.ui.model.application.MUIElement;
import org.eclipse.e4.ui.model.application.MWindow;
import org.eclipse.e4.ui.model.application.MWindowTrim;
import org.eclipse.e4.ui.workbench.swt.internal.AbstractPartRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

public class DragHost {
	public static final String DragHostId = "dragHost"; //$NON-NLS-1$

	MUIElement dragElement;
	MElementContainer<MUIElement> originalParent;
	int originalIndex;
	MWindow baseWindow;

	int xOffset = 20;
	int yOffset = 20;

	private MWindow dragWindow;

	public DragHost(Shell shell) {
		dragWindow = (MWindow) shell.getData(AbstractPartRenderer.OWNING_ME);
		baseWindow = (MWindow) shell.getParent().getData(
				AbstractPartRenderer.OWNING_ME);
		dragElement = dragWindow.getChildren().get(0);
	}

	public DragHost(MUIElement element) {
		assert (dragElement != null);

		dragElement = element;
		originalParent = dragElement.getParent();
		originalIndex = originalParent.getChildren().indexOf(element);

		baseWindow = getWindow();
		assert (baseWindow != null && baseWindow.getWidget() != null);

		attach();
	}

	public Shell getShell() {
		return (Shell) dragWindow.getWidget();
	}

	public MWindow getModel() {
		return dragWindow;
	}

	public void setLocation(int x, int y) {
		getShell().setLocation(x + xOffset, y + yOffset);
	}

	private MWindow getWindow() {
		MUIElement pe = originalParent;
		while (pe != null && !(pe instanceof MApplication)) {
			if (((Object) pe) instanceof MWindow)
				return (MWindow) pe;
			pe = pe.getParent();
		}

		return null;
	}

	private void attach() {
		dragElement.getParent().getChildren().remove(dragElement);
		((Shell) baseWindow.getWidget()).getDisplay().update();
		dragWindow = MApplicationFactory.eINSTANCE.createWindow();
		dragWindow.getTags().add(DragHostId);
		formatModel(dragWindow);

		// define the initial location and size for the window
		Point cp = ((Shell) baseWindow.getWidget()).getDisplay()
				.getCursorLocation();
		Point size = new Point(200, 200);
		if (dragElement.getWidget() instanceof Control) {
			Control ctrl = (Control) dragElement.getWidget();
			size = ctrl.getSize();
		} else if (dragElement.getWidget() instanceof ToolItem) {
			ToolItem ti = (ToolItem) dragElement.getWidget();
			Rectangle bounds = ti.getBounds();
			size = new Point(bounds.width + 3, bounds.height + 3);
		}

		dragWindow.setX(cp.x + xOffset);
		dragWindow.setY(cp.y + yOffset);
		dragWindow.setWidth(size.x);
		dragWindow.setHeight(size.y);

		// add the window as a child oc the base window
		baseWindow.getChildren().add(dragWindow);

		getShell().layout(getShell().getChildren(), SWT.CHANGED | SWT.DEFER);
		getShell().setVisible(true);
	}

	private void formatModel(MWindow dragWindow) {
		if (dragElement instanceof MToolItem) {
			MWindowTrim trim = MApplicationFactory.eINSTANCE.createWindowTrim();
			MToolBar mtb = MApplicationFactory.eINSTANCE.createToolBar();
			trim.getChildren().add(mtb);
			mtb.getChildren().add((MToolItem) dragElement);
			dragWindow.getChildren().add(trim);
		} else if (dragElement instanceof MToolBar) {
			MWindowTrim trim = MApplicationFactory.eINSTANCE.createWindowTrim();
			trim.getChildren().add(dragElement);
			dragWindow.getChildren().add(trim);
		} else if (dragElement instanceof MPSCElement) {
			dragWindow.getChildren().add((MPSCElement) dragElement);
		}
	}

	public void drop(MElementContainer<MUIElement> newContainer, int itemIndex) {
		if (dragElement.getParent() != null)
			dragElement.getParent().getChildren().remove(dragElement);
		if (itemIndex >= 0)
			newContainer.getChildren().add(itemIndex, dragElement);
		else
			newContainer.getChildren().add(dragElement);

		newContainer.setSelectedElement(dragElement);
		if (dragElement.getWidget() instanceof ToolItem) {
			ToolItem ti = (ToolItem) dragElement.getWidget();
			ToolBar tb = ti.getParent();
			tb.layout(true);
			tb.getParent()
					.layout(new Control[] { tb }, SWT.CHANGED | SWT.DEFER);
		}

		baseWindow.getChildren().remove(dragWindow);

		newContainer.setSelectedElement(dragElement);

		if (getShell() != null)
			getShell().dispose();
	}

	public void cancel() {
		drop(originalParent, originalIndex);
	}

	public MUIElement getDragElement() {
		return dragElement;
	}
}
