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

package com.evolveum.midpoint.repo.sql.data.common.dictionary;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.repo.sql.SerializationRelatedException;
import com.evolveum.midpoint.repo.sql.data.common.any.RExtItem;
import com.evolveum.midpoint.schema.util.ExceptionUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.criteria.CriteriaQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO clean this up!
 *
 * @author mederly
 */
public class ExtItemDictionary {

	private static final Trace LOGGER = TraceManager.getTrace(ExtItemDictionary.class);

	private static final ExtItemDictionary INSTANCE = new ExtItemDictionary();

	private ExtItemDictionary() {
	}

	public static ExtItemDictionary getInstance() {
		return INSTANCE;
	}

	private Map<Integer, RExtItem> itemsById;
	private Map<RExtItem.Key, RExtItem> itemsByKey;

	private boolean fetchItemsIfNeeded(@NotNull Session session) {
		if (itemsByKey != null) {
			return false;
		} else {
			fetchItems(session);
			return true;
		}
	}

	private void fetchItems(@NotNull Session session) {
		CriteriaQuery<RExtItem> query = session.getCriteriaBuilder().createQuery(RExtItem.class);
		query.select(query.from(RExtItem.class));
		List<RExtItem> items = session.createQuery(query).getResultList();
		LOGGER.debug("Fetched {} item definitions", items.size());
		
		itemsById = new HashMap<>(items.size());
		itemsByKey = new HashMap<>(items.size());
		for (RExtItem item : items) {
			itemsById.put(item.getId(), item);
			itemsByKey.put(item.toKey(), item);
		}
	}

	//	public RExtItem findItemById(long id) {
	//		return getItemsById().get(id);
	//	}

	@NotNull
	public synchronized RExtItem createOrFindItemDefinition(@NotNull ItemDefinition<?> definition, @NotNull Session session) {
		return createOrFindItemByDefinitionInternal(definition, session, true);
	}

	@Nullable
	public synchronized RExtItem findItemByDefinition(@NotNull ItemDefinition<?> definition, @NotNull Session session) {
		return createOrFindItemByDefinitionInternal(definition, session, false);
	}

	@Contract("_, _, true -> !null")
	private synchronized RExtItem createOrFindItemByDefinitionInternal(@NotNull ItemDefinition<?> definition, @NotNull Session session,
			boolean create) {
		boolean fetchedNow = fetchItemsIfNeeded(session);
		RExtItem.Key key = RExtItem.createKeyFromDefinition(definition);
		RExtItem item = itemsByKey.get(key);
		if (item == null && !fetchedNow) {
			LOGGER.debug("Ext item for {} not found, fetching all items.", key);
			fetchItems(session);
			item = itemsByKey.get(key);
		}
		if (item == null && create) {
			LOGGER.debug("Ext item for {} not found even in current items; creating it.", key);
			item = RExtItem.createFromDefinition(definition);
			try {
				session.persist(item);
			} catch (Throwable t) {
				if (ExceptionUtil.findCause(t, ConstraintViolationException.class) != null) {
					throw new SerializationRelatedException(t);
				} else {
					throw t;
				}
			}
		}
		return item;
	}

	public synchronized void initialize() {
		itemsByKey = null;
		itemsById = null;
	}
}
