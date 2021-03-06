/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
/*
 * Render the Content in the Main Panel corressponding to Service (GwtBSConfigComponent) selected in the Services Panel
 *
 * Fields are rendered based on their type (Password(Input), Choice(Dropboxes) etc. with Text fields rendered
 * for both numeric and other textual field with validate() checking if value in numeric fields is numeric
 */
package org.eclipse.kura.web.client.ui;

import java.util.Iterator;
import java.util.logging.Level;

import org.eclipse.kura.web.client.util.FailureHandler;
import org.eclipse.kura.web.shared.model.GwtConfigComponent;
import org.eclipse.kura.web.shared.model.GwtConfigParameter;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.eclipse.kura.web.shared.service.GwtComponentService;
import org.eclipse.kura.web.shared.service.GwtComponentServiceAsync;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenService;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenServiceAsync;
import org.gwtbootstrap3.client.ui.Alert;
import org.gwtbootstrap3.client.ui.AnchorListItem;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.ButtonGroup;
import org.gwtbootstrap3.client.ui.FieldSet;
import org.gwtbootstrap3.client.ui.Form;
import org.gwtbootstrap3.client.ui.FormGroup;
import org.gwtbootstrap3.client.ui.Modal;
import org.gwtbootstrap3.client.ui.ModalBody;
import org.gwtbootstrap3.client.ui.ModalFooter;
import org.gwtbootstrap3.client.ui.ModalHeader;
import org.gwtbootstrap3.client.ui.NavPills;
import org.gwtbootstrap3.client.ui.PanelBody;
import org.gwtbootstrap3.client.ui.TextBox;
import org.gwtbootstrap3.client.ui.html.Span;
import org.gwtbootstrap3.client.ui.html.Text;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;

public class ServicesUi extends AbstractServicesUi {

    private static final ServicesUiUiBinder uiBinder = GWT.create(ServicesUiUiBinder.class);

    interface ServicesUiUiBinder extends UiBinder<Widget, ServicesUi> {
    }

    private final GwtComponentServiceAsync gwtComponentService = GWT.create(GwtComponentService.class);
    private final GwtSecurityTokenServiceAsync gwtXSRFService = GWT.create(GwtSecurityTokenService.class);

    private boolean dirty, initialized;
    private final GwtConfigComponent originalConfig;

    NavPills menu;
    PanelBody content;
    AnchorListItem service;
    TextBox validated;
    FormGroup validatedGroup;
    EntryClassUi entryClass;
    Modal modal;

    @UiField
    Button apply, reset, delete;
    @UiField
    FieldSet fields;
    @UiField
    Form form;
    @UiField
    Button deleteButton;

    @UiField
    Modal incompleteFieldsModal, deleteModal;
    @UiField
    Alert incompleteFields;
    @UiField
    Text incompleteFieldsText;
    @UiField
    Alert deleteMessage;

    //
    // Public methods
    //
    public ServicesUi(final GwtConfigComponent addedItem, EntryClassUi entryClassUi) {
        initWidget(uiBinder.createAndBindUi(this));
        this.initialized = false;
        this.entryClass = entryClassUi;
        this.originalConfig = addedItem;
        restoreConfiguration(this.originalConfig);
        this.fields.clear();

        this.apply.setText(MSGS.apply());
        this.apply.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                apply();
            }
        });

        this.reset.setText(MSGS.reset());
        this.reset.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                reset();
            }
        });
        
        delete.setText(MSGS.delete());
        delete.addClickHandler(new ClickHandler(){

            @Override
            public void onClick(ClickEvent event) {
                deleteModal.show();
            }});
        
        deleteButton.addClickHandler(new ClickHandler(){

            @Override
            public void onClick(ClickEvent event) {
                delete();
            }});
        deleteMessage.setText(MSGS.deleteWarning());
        
        renderForm();
        initInvalidDataModal();

        setDirty(false);
        this.apply.setEnabled(false);
        this.reset.setEnabled(false);
        delete.setEnabled(m_configurableComponent.isFactoryComponent());
    }

    @Override
    public void setDirty(boolean flag) {
        this.dirty = flag;
        if (this.dirty && this.initialized) {
            this.apply.setEnabled(true);
            this.reset.setEnabled(true);
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void reset() {
        if (isDirty()) {
            // Modal
            this.modal = new Modal();

            ModalHeader header = new ModalHeader();
            header.setTitle(MSGS.confirm());
            this.modal.add(header);

            ModalBody body = new ModalBody();
            body.add(new Span(MSGS.deviceConfigDirty()));
            this.modal.add(body);

            ModalFooter footer = new ModalFooter();
            ButtonGroup group = new ButtonGroup();
            Button yes = new Button();
            yes.setText(MSGS.yesButton());
            yes.addClickHandler(new ClickHandler() {

                @Override
                public void onClick(ClickEvent event) {
                    ServicesUi.this.modal.hide();
                    restoreConfiguration(ServicesUi.this.originalConfig);
                    renderForm();
                    ServicesUi.this.apply.setEnabled(false);
                    ServicesUi.this.reset.setEnabled(false);
                    setDirty(false);
                    ServicesUi.this.entryClass.initServicesTree();
                }
            });
            group.add(yes);
            Button no = new Button();
            no.setText(MSGS.noButton());
            no.addClickHandler(new ClickHandler() {

                @Override
                public void onClick(ClickEvent event) {
                    ServicesUi.this.modal.hide();
                }
            });
            group.add(no);
            footer.add(group);
            this.modal.add(footer);
            this.modal.show();
        }                   // end is dirty
    }
    
    public void delete(){
        if(m_configurableComponent.isFactoryComponent()){
            EntryClassUi.showWaitModal();
            gwtXSRFService.generateSecurityToken(new AsyncCallback<GwtXSRFToken> () {

                @Override
                public void onFailure(Throwable ex) {
                    EntryClassUi.hideWaitModal();
                    FailureHandler.handle(ex);
                }

                @Override
                public void onSuccess(GwtXSRFToken token) {
                    gwtComponentService.deleteFactoryConfiguration(token, m_configurableComponent.getComponentId(), true, new AsyncCallback<Void>() {

                        @Override
                        public void onFailure(Throwable caught) {
                            EntryClassUi.hideWaitModal();
                            errorLogger.log(Level.SEVERE, caught.getLocalizedMessage());
                        }

                        @Override
                        public void onSuccess(Void result) {
                            modal.hide();   
                            logger.info(MSGS.info()+": " + MSGS.deviceConfigDeleted());
                            apply.setEnabled(false);
                            reset.setEnabled(false);
                            setDirty(false);
                            entryClass.initServicesTree();
                            EntryClassUi.hideWaitModal();
                        }
                    });
                }
            });
        }
    }

    // TODO: Separate render methods for each type (ex: Boolean, String,
    // Password, etc.). See latest org.eclipse.kura.web code.
    // Iterates through all GwtConfigParameter in the selected
    // GwtConfigComponent
    @Override
    public void renderForm() {
        this.fields.clear();
        for (GwtConfigParameter param : this.m_configurableComponent.getParameters()) {
            if (param.getCardinality() == 0 || param.getCardinality() == 1 || param.getCardinality() == -1) {
                FormGroup formGroup = new FormGroup();
                renderConfigParameter(param, true, formGroup);
            } else {
                renderMultiFieldConfigParameter(param);
            }
        }
        this.initialized = true;
    }

    @Override
    protected void renderTextField(final GwtConfigParameter param, boolean isFirstInstance, final FormGroup formGroup) {
        super.renderTextField(param, isFirstInstance, formGroup);
        this.fields.add(formGroup);
    }

    @Override
    protected void renderPasswordField(final GwtConfigParameter param, boolean isFirstInstance, FormGroup formGroup) {
        super.renderPasswordField(param, isFirstInstance, formGroup);
        this.fields.add(formGroup);
    }

    @Override
    protected void renderBooleanField(final GwtConfigParameter param, boolean isFirstInstance, FormGroup formGroup) {
        super.renderBooleanField(param, isFirstInstance, formGroup);
        this.fields.add(formGroup);
    }

    @Override
    protected void renderChoiceField(final GwtConfigParameter param, boolean isFirstInstance, FormGroup formGroup) {
        super.renderChoiceField(param, isFirstInstance, formGroup);
        this.fields.add(formGroup);
    }

    //
    // Private methods
    //
    private void apply() {
        if (isValid()) {
            if (isDirty()) {
                // TODO ask for confirmation first
                this.modal = new Modal();

                ModalHeader header = new ModalHeader();
                header.setTitle(MSGS.confirm());
                this.modal.add(header);

                ModalBody body = new ModalBody();
                body.add(new Span(MSGS.deviceConfigConfirmation(this.m_configurableComponent.getComponentName())));
                this.modal.add(body);

                ModalFooter footer = new ModalFooter();
                ButtonGroup group = new ButtonGroup();
                Button yes = new Button();
                yes.setText(MSGS.yesButton());
                yes.addClickHandler(new ClickHandler() {

                    @Override
                    public void onClick(ClickEvent event) {
                        EntryClassUi.showWaitModal();
                        try {
                            getUpdatedConfiguration();
                        } catch (Exception ex) {
                            EntryClassUi.hideWaitModal();
                            FailureHandler.handle(ex);
                            return;
                        }
                        ServicesUi.this.gwtXSRFService.generateSecurityToken(new AsyncCallback<GwtXSRFToken>() {

                            @Override
                            public void onFailure(Throwable ex) {
                                EntryClassUi.hideWaitModal();
                                FailureHandler.handle(ex);
                            }

                            @Override
                            public void onSuccess(GwtXSRFToken token) {
                                ServicesUi.this.gwtComponentService.updateComponentConfiguration(token,
                                        ServicesUi.this.m_configurableComponent, new AsyncCallback<Void>() {

                                    @Override
                                    public void onFailure(Throwable caught) {
                                        EntryClassUi.hideWaitModal();
                                        FailureHandler.handle(caught);
                                        errorLogger.log(
                                                Level.SEVERE, caught.getLocalizedMessage() != null
                                                        ? caught.getLocalizedMessage() : caught.getClass().getName(),
                                                caught);
                                    }

                                    @Override
                                    public void onSuccess(Void result) {
                                        ServicesUi.this.modal.hide();
                                        logger.info(MSGS.info() + ": " + MSGS.deviceConfigApplied());
                                        ServicesUi.this.apply.setEnabled(false);
                                        ServicesUi.this.reset.setEnabled(false);
                                        setDirty(false);
                                        ServicesUi.this.entryClass.initServicesTree();
                                        EntryClassUi.hideWaitModal();
                                    }
                                });

                            }
                        });
                    }
                });
                group.add(yes);
                Button no = new Button();
                no.setText(MSGS.noButton());
                no.addClickHandler(new ClickHandler() {

                    @Override
                    public void onClick(ClickEvent event) {
                        ServicesUi.this.modal.hide();
                    }
                });
                group.add(no);
                footer.add(group);
                this.modal.add(footer);
                this.modal.show();

                // ----

            }                   // end isDirty()
        } else {
            errorLogger.log(Level.SEVERE, "Device configuration error!");
            this.incompleteFieldsModal.show();
        }                   // end else isValid
    }

    private GwtConfigComponent getUpdatedConfiguration() {
        Iterator<Widget> it = this.fields.iterator();
        while (it.hasNext()) {
            Widget w = it.next();
            if (w instanceof FormGroup) {
                FormGroup fg = (FormGroup) w;
                fillUpdatedConfiguration(fg);
            }
        }
        return this.m_configurableComponent;
    }

    private void initInvalidDataModal() {
        this.incompleteFieldsModal.setTitle(MSGS.warning());
        this.incompleteFieldsText.setText(MSGS.formWithErrorsOrIncomplete());
    }
}
