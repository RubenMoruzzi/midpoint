/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 */
package com.evolveum.midpoint.model.intest.manual;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Listeners;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ChangeTypeType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

/**
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class TestSemiManualDisable extends TestSemiManual {

	private static final Trace LOGGER = TraceManager.getTrace(TestSemiManualDisable.class);

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
	}
	
	@Override
	protected BackingStore createBackingStore() {
		return new CsvDisablingBackingStore();
	}

	@Override
	protected String getResourceOid() {
		return RESOURCE_SEMI_MANUAL_DISABLE_OID;
	}

	@Override
	protected File getResourceFile() {
		return RESOURCE_SEMI_MANUAL_DISABLE_FILE;
	}

	@Override
	protected String getRoleOneOid() {
		return ROLE_ONE_SEMI_MANUAL_DISABLE_OID;
	}

	@Override
	protected File getRoleOneFile() {
		return ROLE_ONE_SEMI_MANUAL_DISABLE_FILE;
	}

	@Override
	protected String getRoleTwoOid() {
		return ROLE_TWO_SEMI_MANUAL_DISABLE_OID;
	}

	@Override
	protected File getRoleTwoFile() {
		return ROLE_TWO_SEMI_MANUAL_DISABLE_FILE;
	}

	@Override
	protected boolean nativeCapabilitiesEntered() {
		return true;
	}
	
	@Override
	protected void assertUnassignedShadow(PrismObject<ShadowType> shadow, ActivationStatusType expectAlternativeActivationStatus) {
		assertShadowNotDead(shadow);
		assertShadowActivationAdministrativeStatus(shadow, expectAlternativeActivationStatus);
	}

	@Override
	protected void assertUnassignedFuture(PrismObject<ShadowType> shadowModelFuture, boolean assertPassword) {
		assertShadowActivationAdministrativeStatus(shadowModelFuture, ActivationStatusType.DISABLED);
		if (assertPassword) {
			assertShadowPassword(shadowModelFuture);
		}
	}

	@Override
	protected void assertDeprovisionedTimedOutUser(PrismObject<UserType> userAfter, String accountOid) throws Exception {
		assertLinks(userAfter, 1);
		PrismObject<ShadowType> shadowModel = getShadowModel(accountOid);
		display("Model shadow", shadowModel);
		assertShadowActivationAdministrativeStatus(shadowModel, ActivationStatusType.DISABLED);
	}

	@Override
	protected void assertWillUnassignPendingOperation(PrismObject<ShadowType> shadowRepo, OperationResultStatusType expectedStatus) {
		PendingOperationType pendingOperation = findPendingOperation(shadowRepo,
				null, OperationResultStatusType.IN_PROGRESS, 
				ChangeTypeType.MODIFY, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
		if (expectedStatus == OperationResultStatusType.IN_PROGRESS) {
			assertPendingOperation(shadowRepo, pendingOperation,
					accountWillSecondReqestTimestampStart, accountWillSecondReqestTimestampEnd,
					OperationResultStatusType.IN_PROGRESS,
					null, null);
		} else {
			pendingOperation = findPendingOperation(shadowRepo, 
					null, OperationResultStatusType.SUCCESS, 
					ChangeTypeType.MODIFY, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
			assertPendingOperation(shadowRepo, pendingOperation,
					accountWillSecondReqestTimestampStart, accountWillSecondReqestTimestampEnd,
					OperationResultStatusType.SUCCESS,
					accountWillCompletionTimestampStart, accountWillCompletionTimestampEnd);
			assertNotNull("No ID in pending operation", pendingOperation.getId());
		}
		assertNotNull("No ID in pending operation", pendingOperation.getId());
	}

	@Override
	protected void cleanupUser(final String TEST_NAME, String userOid, String username, String accountOid) throws Exception {

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		backingStore.deleteAccount(username);
		try {
			repositoryService.deleteObject(ShadowType.class, accountOid, result);
		} catch (ObjectNotFoundException e) {
			// no problem
		}
		recomputeUser(userOid, task, result);

		PrismObject<UserType> userAfter = getUser(userOid);
		display("User after", userAfter);
		assertLinks(userAfter, 0);
		assertNoShadow(accountOid);
	}

	@Override
	protected void assertTest526Deltas(PrismObject<ShadowType> shadowRepo, OperationResult result) {
		assertPendingOperationDeltas(shadowRepo, 3);

		ObjectDeltaType deltaModify = null;
		ObjectDeltaType deltaAdd = null;
		ObjectDeltaType deltaDisable = null;
		for (PendingOperationType pendingOperation: shadowRepo.asObjectable().getPendingOperation()) {
			ObjectDeltaType delta = pendingOperation.getDelta();
			if (ChangeTypeType.ADD.equals(delta.getChangeType())) {
				deltaAdd = delta;
				assertEquals("Wrong status in add delta", OperationResultStatusType.SUCCESS, pendingOperation.getResultStatus());
			}
			if (ChangeTypeType.MODIFY.equals(delta.getChangeType()) && OperationResultStatusType.SUCCESS.equals(pendingOperation.getResultStatus())) {
				deltaModify = delta;
			}
			if (ChangeTypeType.MODIFY.equals(delta.getChangeType()) && OperationResultStatusType.IN_PROGRESS.equals(pendingOperation.getResultStatus())) {
				deltaDisable = delta;
			}
		}
		assertNotNull("No add pending delta", deltaAdd);
		assertNotNull("No modify pending delta", deltaModify);
		assertNotNull("No disable pending delta", deltaDisable);
	}

	@Override
	protected void assertTest528Deltas(PrismObject<ShadowType> shadowRepo, OperationResult result) {
		assertPendingOperationDeltas(shadowRepo, 3);

		ObjectDeltaType deltaModify = null;
		ObjectDeltaType deltaAdd = null;
		ObjectDeltaType deltaDelete = null;
		for (PendingOperationType pendingOperation: shadowRepo.asObjectable().getPendingOperation()) {
			assertEquals("Wrong status in pending delta", OperationResultStatusType.SUCCESS, pendingOperation.getResultStatus());
		}
	}
}
