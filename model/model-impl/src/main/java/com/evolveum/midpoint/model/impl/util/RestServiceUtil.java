/*
 * Copyright (c) 2010-2016 Evolveum
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

package com.evolveum.midpoint.model.impl.util;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.impl.security.SecurityHelper;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import org.apache.cxf.jaxrs.ext.MessageContext;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;

/**
 * @author mederly (only copied existing code)
 */
public class RestServiceUtil {

	public static final String MESSAGE_PROPERTY_TASK_NAME = "task";
	private static final String QUERY_PARAMETER_OPTIONS = "options";
	public static final String OPERATION_RESULT_STATUS = "OperationResultStatus";
	public static final String OPERATION_RESULT_MESSAGE = "OperationResultMessage";

	public static Response handleException(Exception ex) {
		return createErrorResponseBuilder(ex).build();
	}

	public static Response.ResponseBuilder createErrorResponseBuilder(Exception ex) {
		if (ex instanceof ObjectNotFoundException) {
			return createErrorResponseBuilder(Response.Status.NOT_FOUND, ex);
		}

		if (ex instanceof CommunicationException) {
			return createErrorResponseBuilder(Response.Status.GATEWAY_TIMEOUT, ex);
		}

		if (ex instanceof SecurityViolationException) {
			return createErrorResponseBuilder(Response.Status.FORBIDDEN, ex);
		}

		if (ex instanceof ConfigurationException) {
			return createErrorResponseBuilder(Response.Status.BAD_GATEWAY, ex);
		}

		if (ex instanceof SchemaException
				|| ex instanceof PolicyViolationException
				|| ex instanceof ConsistencyViolationException
				|| ex instanceof ObjectAlreadyExistsException) {
			return createErrorResponseBuilder(Response.Status.CONFLICT, ex);
		}

		return createErrorResponseBuilder(Response.Status.INTERNAL_SERVER_ERROR, ex);
	}

	private static Response.ResponseBuilder createErrorResponseBuilder(Response.Status status, Exception ex) {
		return createErrorResponseBuilder(status, ex.getMessage());
	}

	public static Response.ResponseBuilder createErrorResponseBuilder(Response.Status status, String message) {
		return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN);
	}

	public static ModelExecuteOptions getOptions(UriInfo uriInfo){
    	List<String> options = uriInfo.getQueryParameters().get(QUERY_PARAMETER_OPTIONS);
		return ModelExecuteOptions.fromRestOptions(options);
    }

	public static Task initRequest(MessageContext mc) {
		// No need to audit login. it was already audited during authentication
		return (Task) mc.get(MESSAGE_PROPERTY_TASK_NAME);
	}

	public static void finishRequest(Task task, SecurityHelper securityHelper) {
		task.getResult().computeStatus();
		ConnectionEnvironment connEnv = new ConnectionEnvironment();
		connEnv.setChannel(SchemaConstants.CHANNEL_REST_URI);
		connEnv.setSessionId(task.getTaskIdentifier());
		securityHelper.auditLogout(connEnv, task);
	}

	// slightly experimental
	public static Response.ResponseBuilder createResultHeaders(Response.ResponseBuilder builder, OperationResult result) {
		return builder
				.header(OPERATION_RESULT_STATUS, result.getStatus())
				.header(OPERATION_RESULT_MESSAGE, result.getMessage());
	}
}
