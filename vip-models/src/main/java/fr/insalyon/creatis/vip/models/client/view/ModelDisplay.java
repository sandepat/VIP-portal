/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.insalyon.creatis.vip.models.client.view;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.tile.TileGrid;
import com.smartgwt.client.widgets.toolbar.ToolStrip;
import com.smartgwt.client.widgets.toolbar.ToolStripButton;
import fr.cnrs.i3s.neusemstore.vip.semantic.simulation.model.client.bean.SimulationObjectModel;
import fr.cnrs.i3s.neusemstore.vip.semantic.simulation.model.client.bean.SimulationObjectModelUtil;
import fr.insalyon.creatis.vip.core.client.view.ModalWindow;
import fr.insalyon.creatis.vip.core.client.view.layout.Layout;
import fr.insalyon.creatis.vip.datamanager.client.DataManagerConstants;
import fr.insalyon.creatis.vip.datamanager.client.rpc.DataManagerService;
import fr.insalyon.creatis.vip.datamanager.client.rpc.DataManagerServiceAsync;
import fr.insalyon.creatis.vip.datamanager.client.view.DataManagerSection;
import fr.insalyon.creatis.vip.datamanager.client.view.browser.BrowserLayout;
import fr.insalyon.creatis.vip.datamanager.client.view.operation.OperationLayout;
import fr.insalyon.creatis.vip.models.client.ModelConstants;
import fr.insalyon.creatis.vip.models.client.rpc.ModelService;
import fr.insalyon.creatis.vip.models.client.rpc.ModelServiceAsync;
import java.util.Date;

/**
 *
 * @author glatard
 */
public class ModelDisplay extends VLayout {

    protected ModalWindow modal = new ModalWindow(this);
    protected SimulationObjectModel model = null;
    protected ToolStrip toolStrip;
    private ToolStripButton download, upload;
    ModelTreeGrid modelTreeGrid;
    private String zipFile;

    ModelDisplay(String uri) {
        super();
        buildModel(uri);
    }

    ModelDisplay(SimulationObjectModel result) {
        super();
        this.model = result;
        init();
    }

    public void setZipFile(String z) {
        zipFile = z;
    }

    private void init() {
        toolStrip = new ToolStrip();
        toolStrip.setWidth100();
        addMember(toolStrip);
        modelTreeGrid = new ModelTreeGrid(model);
        addMember(modelTreeGrid);
        if (model != null) {
            enableDownload();
            checkModel(model);
        }
    }

    public void disableCommit() {
        if(upload != null)
            upload.hide();
    }

    public void enableCommit() {
        upload = new ToolStripButton("Commit model");
        upload.setIcon(DataManagerConstants.ICON_UPLOAD);
        upload.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {

                if (zipFile == null) {
                    SC.warn("No zip file");//TODO: build it
                    return;
                }

                if (model == null) {
                    SC.warn("No simulation object model to commit.");
                    return;
                }
                ////TODO put annotations in ZIP file in case they were modified
                modal.show("Uploading " + zipFile, true);
                final String lfn = uploadModel(zipFile);

                modal.show("Committing annotations to the Triple Store", true);
                //commit rdf annotations

                ModelServiceAsync ssu = ModelService.Util.getInstance();
                AsyncCallback<SimulationObjectModel> cbssu = new AsyncCallback<SimulationObjectModel>() {

                    public void onFailure(Throwable caught) {
                        SC.warn("Cannot set the model storage URL");
                    }

                    public void onSuccess(SimulationObjectModel result) {
                        final String uri = result.getURI();
                        ModelServiceAsync mms = ModelService.Util.getInstance();
                        AsyncCallback<Void> callback1 = new AsyncCallback<Void>() {

                            public void onFailure(Throwable caught) {
                                modal.hide();
                                SC.warn("Cannot commit model to the Triple Store");
                            }

                            public void onSuccess(Void result) {
                                modal.hide();
                                SC.say("Model successfully comitted to the Triple Store (" + uri + ")");
                                ModelListTab modelsTab = (ModelListTab) Layout.getInstance().getTab("model-browse-tab");
                                if (modelsTab != null) {
                                    modelsTab.loadModels();
                                }
                                OperationLayout.getInstance().loadData();
                                OperationLayout.getInstance().activateAutoRefresh();
                            }
                        };
                        mms.completeModel(result, callback1);
                    }
                };
                ssu.setStorageUrl(model, lfn, cbssu);
            }
        });
        upload.setTooltip("Commit model to the repository");
        toolStrip.addButton(upload);

    }

    private void enableDownload() {
        download = new ToolStripButton("Download");
        download.setIcon(DataManagerConstants.ICON_DOWNLOAD);
        download.setTooltip("Download model files");
        toolStrip.addButton(download);
        download.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                String lfn = model.getStorageURL();
                downloadModel(lfn);
            }
        });
    }

    private String uploadModel(String file) {

        //uploading zip file
        AsyncCallback<Void> callback = new AsyncCallback<Void>() {

            public void onFailure(Throwable caught) {
                SC.warn("Cannot upload zip file");
            }

            public void onSuccess(Void result) {
                SC.say("Added zip file to the upload pool");
                ((DataManagerSection) Layout.getInstance().getMainSection(DataManagerConstants.SECTION_FILE_TRANSFER)).expand();
                BrowserLayout.getInstance().loadData(ModelConstants.MODEL_HOME, true);
            }
        };
        DataManagerServiceAsync service = DataManagerService.Util.getInstance();
        String remoteDir = ModelConstants.MODEL_HOME;
        //TODO: check if this exists
        String remoteName = getTimeStampMilli() + "-" + file;
        String localName = file;
        service.uploadFile(localName, remoteName, remoteDir, callback);
        upload.disable();
        return remoteDir + "/" + remoteName;
    }

    private long getTimeStampMilli() {
        Date date = new Date();
        return date.getTime();
    }

    private void disableDownload(String lfn,boolean hide) {
        if(hide)
            download.hide();
        download.setIcon(ModelConstants.APP_IMG_KO);
        download.setTooltip("Cannot find model files (" + lfn + ")");
    }

    public void checkModel(final SimulationObjectModel model) {
        final String lfn = model.getStorageURL();
        DataManagerServiceAsync service = DataManagerService.Util.getInstance();

        AsyncCallback<Boolean> callback = new AsyncCallback<Boolean>() {

            public void onFailure(Throwable caught) {
                modal.hide();
                disableDownload(lfn,false);
            }

            public void onSuccess(Boolean result) {
                modal.hide();

               
            }
        };
        if (model.getStorageURL() != null) {

            modal.show("Checking if model files exist", true);
            service.exists(lfn, callback);
        } else {
            disableDownload("No storage URL",true);
        }
        
        if (SimulationObjectModelUtil.isReadyForSimulation(model, SimulationObjectModelUtil.Modality.IRM)) {
            toolStrip.addMember(modalityButton("MRI", true));
        } else {
            toolStrip.addMember(modalityButton("MRI", false));
        }
        if (SimulationObjectModelUtil.isReadyForSimulation(model, SimulationObjectModelUtil.Modality.CT)) {
            toolStrip.addMember(modalityButton("CT", true));
        } else {
            toolStrip.addMember(modalityButton("CT", false));
        }
        if (SimulationObjectModelUtil.isReadyForSimulation(model, SimulationObjectModelUtil.Modality.PET)) {
            toolStrip.addMember(modalityButton("PET", true));
        } else {
            toolStrip.addMember(modalityButton("PET", false));
        }
        if (SimulationObjectModelUtil.isReadyForSimulation(model, SimulationObjectModelUtil.Modality.US)) {
            toolStrip.addMember(modalityButton("Ultrasound", true));
        } else {
            toolStrip.addMember(modalityButton("Ultrasound", false));
        }
    }

    private void downloadModel(final String lfnModel) {

        DataManagerServiceAsync service = DataManagerService.Util.getInstance();
        AsyncCallback<Void> callback = new AsyncCallback<Void>() {

            public void onFailure(Throwable caught) {
                SC.warn("Cannot download model file.");
            }

            public void onSuccess(Void result) {
                SC.say("Model file download is in progress.");
                ((DataManagerSection) Layout.getInstance().getMainSection(DataManagerConstants.SECTION_FILE_TRANSFER)).expand();
            }
        };
        service.downloadFile(lfnModel, callback);
    }

    private ToolStripButton modalityButton(String caption, boolean test) {
        ToolStripButton ok = new ToolStripButton(caption);
        if (test) {
            ok.setIcon(ModelConstants.APP_IMG_OK);
            ok.setTooltip("This model can be used in a simulation of this modality.");
        } else {
            ok.setTooltip("This model lacks physical parameters to be used in a simulation of this modality.");
            ok.setIcon(ModelConstants.APP_IMG_KO);
        }
        return ok;
    }

    private void buildModel(final String uri) {
        ModelServiceAsync ms = ModelService.Util.getInstance();
        final AsyncCallback<SimulationObjectModel> callback = new AsyncCallback<SimulationObjectModel>() {

            public void onFailure(Throwable caught) {
                modal.hide();
                SC.say("Cannot load model (" + uri + ")");
            }

            public void onSuccess(SimulationObjectModel result) {
                modal.hide();
                if (result != null) {
                    model = result;
                    //SC.say("Model loaded.");
                    init();
                } else {
                    SC.say("Cannot load model");
                }
            }
        };
        modal.show("Loading model", true);
        ms.rebuildObjectModelFromTripleStore(uri, callback);
    }
}
