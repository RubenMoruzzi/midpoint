/*
 * Copyright (c) 2013 Evolveum
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
package com.evolveum.midpoint.model.trigger;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.ModelConstants;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.model.lens.Clockwork;
import com.evolveum.midpoint.model.lens.LensContext;
import com.evolveum.midpoint.model.lens.LensUtil;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.provisioning.api.ChangeNotificationDispatcher;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResourceObjectChangeListener;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;

/**
 * @author Radovan Semancik
 *
 */
@Component
public class RecomputeTriggerHandler implements TriggerHandler {
	
	public static final String HANDLER_URI = ModelConstants.NS_MODEL_TRIGGER_PREFIX + "/recompute/handler-2";
	
	private static final transient Trace LOGGER = TraceManager.getTrace(RecomputeTriggerHandler.class);

	@Autowired(required = true)
	private TriggerHandlerRegistry triggerHandlerRegistry;
	
	@Autowired(required = true)
    private Clockwork clockwork;
	
	@Autowired(required=true)
	private PrismContext prismContext;
	
	@Autowired(required = true)
    private ProvisioningService provisioningService;
	
	@Autowired(required = true)
	private ChangeNotificationDispatcher changeNotificationDispatcher;
	
	@PostConstruct
	private void initialize() {
		triggerHandlerRegistry.register(HANDLER_URI, this);
	}
	
	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.model.trigger.TriggerHandler#handle(com.evolveum.midpoint.prism.PrismObject)
	 */
	@Override
	public <O extends ObjectType> void handle(PrismObject<O> object, Task task, OperationResult result) {
		try {
			if (object.canRepresent(UserType.class)) {
				recomputeUser((PrismObject<UserType>)object, task, result);
			} else if (object.canRepresent(ShadowType.class)) {
				recomputeShadow((PrismObject<ShadowType>)object, task, result);
			} else {
				LOGGER.error("Cannot recompute {}", object);
			}
		} catch (SchemaException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ObjectNotFoundException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ExpressionEvaluationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (CommunicationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ObjectAlreadyExistsException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (ConfigurationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (PolicyViolationException e) {
			LOGGER.error(e.getMessage(), e);
		} catch (SecurityViolationException e) {
			LOGGER.error(e.getMessage(), e);
		}

	}
	
	private void recomputeUser(PrismObject<UserType> user, Task task, OperationResult result) throws SchemaException, 
			ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ObjectAlreadyExistsException, 
			ConfigurationException, PolicyViolationException, SecurityViolationException {
		LOGGER.trace("Recomputing user {}", user);
		LensContext<UserType, ShadowType> syncContext = LensUtil.createRecomputeContext(UserType.class, user, prismContext, provisioningService);
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Recomputing of user {}: context:\n{}", user, syncContext.dump());
		}
		clockwork.run(syncContext, task, result);
		LOGGER.trace("Recomputing of user {}: {}", user, result.getStatus());
	}

	private void recomputeShadow(PrismObject<ShadowType> shadow, Task task, OperationResult result) throws SchemaException, 
			ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ObjectAlreadyExistsException, 
			ConfigurationException, PolicyViolationException, SecurityViolationException {
		LOGGER.trace("Recomputing shadow {}", shadow);

		String resourceOid = ShadowUtil.getResourceOid(shadow);
		PrismObject<ResourceType> resource = provisioningService.getObject(ResourceType.class, resourceOid, null, result);
		
		ResourceObjectShadowChangeDescription change = new ResourceObjectShadowChangeDescription();
		change.setSourceChannel(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_RECOMPUTE));
		change.setResource(resource);
		change.setOldShadow(shadow);
		
		PrismObject<ShadowType> fullShadow = provisioningService.getObject(ShadowType.class, shadow.getOid(), null, result);
		change.setCurrentShadow(fullShadow);
		
		changeNotificationDispatcher.notifyChange(change, task, result);
		
		LOGGER.trace("Recomputing of shadow {}: {}", shadow, result.getStatus());
	}

}
