package com.company.sales.web.order;

import com.company.sales.entity.Order;
import com.company.sales.entity.OrderLine;
import com.haulmont.bpm.entity.ProcActor;
import com.haulmont.bpm.entity.ProcDefinition;
import com.haulmont.bpm.entity.ProcInstance;
import com.haulmont.bpm.gui.procactions.ProcActionsFrame;
import com.haulmont.bpm.service.ProcessRuntimeService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.Button;
import com.haulmont.cuba.gui.components.GroupBoxLayout;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.security.global.UserSession;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.*;

public class OrderEdit extends AbstractEditor<Order> {

    private static final String PROCESS_CODE = "orderApproval";

    @Inject
    private CollectionDatasource<OrderLine, UUID> linesDs;
    @Inject
    private ProcActionsFrame procActionsFrame;
    @Inject
    private GroupBoxLayout procActionsBox;
    @Inject
    private DataManager dataManager;
    @Inject
    private Metadata metadata;
    @Inject
    private UserSession userSession;
    @Inject
    private ProcessRuntimeService processRuntimeService;
    @Inject
    private Button startProcessBtn;

    @Override
    protected void initNewItem(Order item) {
        startProcessBtn.setVisible(true);
    }

    @Override
    public void init(
            Map<String, Object> params) {
        linesDs.addCollectionChangeListener(e -> calculateAmount());
        startProcessBtn.setVisible(!processExists());
    }

    @Override
    protected boolean preCommit() {
        calculateAmount();
        return super.preCommit();
    }

    @Override
    protected void postInit() {
        initProcActionsFrame();
        if (!PersistenceHelper.isNew(getItem())) {
            procActionsBox.setVisible(true);
        }
    }

    private boolean processExists() {
        ProcDefinition procDefinition = dataManager.load(
                LoadContext.create(ProcDefinition.class)
                        .setQuery(new LoadContext.Query("select pd from bpm$ProcDefinition pd where pd.code = :code")
                                .setParameter("code", PROCESS_CODE))
                        .setView("procDefinition-procInstanceEdit")
        );
        return (processRuntimeService.getActiveProcessesCount(procDefinition) > 0);
    }

    private void initProcActionsFrame() {
        if (!PersistenceHelper.isNew(getItem())) {
            procActionsFrame.initializer()
                    .setBeforeStartProcessPredicate(this::commit)
                    .setAfterStartProcessListener(() -> {
                        showNotification("Process started");

                        close(CLOSE_ACTION_ID);
                    })
                    .setBeforeCompleteTaskPredicate(this::commit)
                    .setAfterCompleteTaskListener(() -> {
                        showNotification("Task completed");
                        calculateAmount();
                        close(COMMIT_ACTION_ID);
                    })
                    .setCancelProcessEnabled(false)
                    .init(PROCESS_CODE, getItem());
        }
    }

    private void calculateAmount() {
        BigDecimal amount = BigDecimal.ZERO;
        for (OrderLine line : linesDs.getItems()) {
            amount = amount.add(line.getProduct().getPrice().multiply(line.getQuantity()));
        }
        getItem().setAmount(amount);
    }

    public void startProcess() {
        commit();

        ProcDefinition procDefinition = dataManager.load(
                LoadContext.create(ProcDefinition.class)
                        .setQuery(new LoadContext.Query("select pd from bpm$ProcDefinition pd where pd.code = :code")
                                .setParameter("code", PROCESS_CODE))
                        .setView("procDefinition-procInstanceEdit")
        );

        ProcInstance procInstance = metadata.create(ProcInstance.class);
        procInstance.setProcDefinition(procDefinition);
        procInstance.setObjectEntityId(getItem().getId());
        procInstance.setEntityName(getItem().getMetaClass().getName());

        Set<ProcActor> procActors = new HashSet<>();

        ProcActor manager = metadata.create(ProcActor.class);
        manager.setUser(userSession.getUser());
        manager.setOrder(0);
        manager.setProcInstance(procInstance);
        manager.setProcRole(procDefinition.getProcRoles().get(0));

        procActors.add(manager);
        procInstance.setProcActors(procActors);

        Set<Entity> committed = dataManager.commit(new CommitContext()
                .addInstanceToCommit(manager)
                .addInstanceToCommit(procInstance));

        ProcInstance committedProcInstance = (ProcInstance) committed.stream()
                .filter(p -> p.equals(procInstance))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Error"));

        processRuntimeService.startProcess(committedProcInstance, "Started!", new HashMap<>());
        close(COMMIT_ACTION_ID);
    }
}