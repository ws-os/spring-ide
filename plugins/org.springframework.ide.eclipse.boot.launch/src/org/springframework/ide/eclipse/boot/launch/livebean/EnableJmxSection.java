/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.livebean;

import java.util.EnumSet;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Text;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport.Feature;
import org.springframework.ide.eclipse.boot.launch.util.DelegatingLaunchConfigurationTabSection;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.UIConstants;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * LaunchTabSection that enables LiveBean graph support by adding required
 * VM and program arguments.
 *
 * @author Kris De Volder
 */
public class EnableJmxSection extends DelegatingLaunchConfigurationTabSection {

	private static final boolean DEBUG = false;//(""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	static class UI extends WizardPageSection {
		private Button jmxCheckbox;
		private Button liveBeanCheckbox;
		private Button lifeCycleCheckbox;
		private Text portWidget;
		private EnableJmxFeaturesModel model;
		private Text terminationTimeoutWidget;

		public UI(IPageWithSections owner, EnableJmxFeaturesModel model) {
			super(owner);
			this.model = model;
		}

		@Override
		public void createContents(Composite page) {
			Composite composite = new Composite(page, SWT.NONE);
			composite.setLayout(GridLayoutFactory.fillDefaults().numColumns(3).create());
			jmxCheckbox = new Button(composite, SWT.CHECK);
			jmxCheckbox.setText("Enable JMX");
			jmxCheckbox.setToolTipText(computeTooltipText(
					"Enables JMX. This is required for Live Bean support and Life Cycle Management." +
					"It also allows STS to access information about request-mappings on your app. "+
					"Adds these vm args:\n", Feature.JMX));

			String portToolTip = "The port used for communicating with JMX beans (0 means STS should pick the port automatically on startup). "
					+ "The same port is used/shared by both 'Life Cycle Management' and the 'Live Beans Graph'";
			Label label = new Label(composite, SWT.NONE);
			label.setText("Port:");
			portWidget = new Text(composite, SWT.BORDER);
			GridDataFactory.fillDefaults().hint(UIConstants.fieldLabelWidthHint(portWidget, 7), SWT.DEFAULT)
				.applyTo(portWidget);
			label.setToolTipText(portToolTip);
			portWidget.setToolTipText(portToolTip);

			composite = new Composite(page, SWT.NONE);
			GridLayout layout = GridLayoutFactory.fillDefaults().numColumns(3).margins(0, 0).create();
			layout.marginLeft = UIConstants.fieldLabelWidthHint(label, 4);
			composite.setLayout(layout);
			liveBeanCheckbox = new Button(composite, SWT.CHECK);
			liveBeanCheckbox.setText("Enable Live Bean support.");
			liveBeanCheckbox.setToolTipText(computeTooltipText(
					"Enables support for Live Beans Graph View by adding vm args:\n",
					Feature.LIVE_BEAN_GRAPH));
			GridDataFactory.fillDefaults().span(3, 1).applyTo(liveBeanCheckbox);

			lifeCycleCheckbox = new Button(composite, SWT.CHECK);
			lifeCycleCheckbox.setText("Enable Life Cycle Management.");
			lifeCycleCheckbox.setToolTipText(computeTooltipText(
					"Requires Boot 1.3.0. Allows Boot Dashboard View to track 'STARTING' state of Boot Apps; allows STS to ask Boot Apps to shutdown nicely. " +
					"Adds these vm args: \n",
					Feature.LIFE_CYCLE));
			Label terminationTimeoutLabel = new Label(composite, SWT.NONE);
			terminationTimeoutLabel.setText("Termination timeout (ms):");
			terminationTimeoutWidget = new Text(composite, SWT.BORDER);
			GridDataFactory.fillDefaults().hint(UIConstants.fieldLabelWidthHint(terminationTimeoutWidget, 7), SWT.DEFAULT)
				.applyTo(terminationTimeoutWidget);
			terminationTimeoutWidget.setToolTipText("How long STS should wait, after asking Boot App nicely to stop, before attemptting to kill the process more forcibly.");

			model.jmxEnabled.addListener((exp, enable) -> {
				portWidget.setEnabled(enable);
				liveBeanCheckbox.setEnabled(enable);
				lifeCycleCheckbox.setEnabled(enable);
				terminationTimeoutLabel.setEnabled(enable);
			});
			model.lifeCycleEnabled.addListener(new ValueListener<Boolean>() {
				@Override
				public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
					terminationTimeoutWidget.setEnabled(value);
				}
			});

			connectCheckbox(model.jmxEnabled, jmxCheckbox);
			connectCheckbox(model.liveBeanEnabled, liveBeanCheckbox);
			connectCheckbox(model.lifeCycleEnabled, lifeCycleCheckbox);

			connectTextWidget(model.port, portWidget);
			connectTextWidget(model.terminationTimeout, terminationTimeoutWidget);
		}


		private static void connectTextWidget(final LiveVariable<String> model, final Text widget) {
			model.addListener(new ValueListener<String>() {
				public void gotValue(LiveExpression<String> exp, String value) {
					String oldValue = widget.getText();
					if (!oldValue.equals(value)) {
						widget.setText(value);
					}
				}
			});
			widget.addModifyListener(new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					model.setValue(widget.getText());
				}
			});
		}

		private String computeTooltipText(String baseMsg, Feature feature) {
			if (feature==Feature.JMX) {
				return baseMsg +
						JmxBeanSupport.jmxBeanVmArgs("${jmxPort}", EnumSet.of(feature));
			} else {
				return baseMsg +
						feature.vmArg;
			}
		}

		@Override
		public LiveExpression<ValidationResult> getValidator() {
			return model.getValidator();
		}
	}

	public EnableJmxSection(IPageWithSections owner, EnableJmxFeaturesModel model) {
		super(owner, model, new UI(owner, model));
	}

	private static void connectCheckbox(final LiveVariable<Boolean> checkedState, final Button widget) {
		final String name = widget.getText();
		checkedState.addListener(new ValueListener<Boolean>() {
			public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
				boolean enable = value!=null && value;
				debug("Widget '"+name+"' <- "+enable);
				widget.setSelection(enable);
			}

		});
		widget.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				boolean enable = widget.getSelection();
				debug("Model '"+name+"' <- "+enable);
				checkedState.setValue(enable);
			}
		});
	}

}
