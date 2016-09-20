/*
 * Copyright 2016 Sai Pullabhotla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jmethods.catatumbo.impl;

import static com.jmethods.catatumbo.impl.DatastoreUtils.*;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.datastore.Batch;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.datastore.DatastoreWriter;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Transaction;
import com.jmethods.catatumbo.DatastoreKey;
import com.jmethods.catatumbo.EntityManagerException;
import com.jmethods.catatumbo.OptimisticLockException;

/**
 * Worker class for performing write operations on the Cloud Datastore.
 * 
 * @author Sai Pullabhotla
 *
 */
public class DefaultDatastoreWriter {

	/**
	 * Reference to the native DatastoreWriter for updating the Cloud Datastore.
	 * This could be the {@link Datastore}, {@link Transaction} or
	 * {@link Batch}.
	 */
	private DatastoreWriter nativeWriter;

	/**
	 * A reference to the Datastore
	 */
	private Datastore datastore;

	/**
	 * Creates a new instance of <code>DefaultDatastoreWriter</code>.
	 * 
	 * @param datastore
	 *            a reference to the {@link Datastore}.
	 */
	public DefaultDatastoreWriter(Datastore datastore) {
		this.datastore = datastore;
		this.nativeWriter = datastore;
	}

	/**
	 * Creates a new instance of <code>DefaultDatastoreWriter</code> for
	 * executing batch updates.
	 * 
	 * @param batch
	 *            the {@link Batch}.
	 */
	public DefaultDatastoreWriter(Batch batch) {
		this.datastore = batch.datastore();
		this.nativeWriter = batch;
	}

	/**
	 * Creates a new instance of <code>DefaultDatastoreWriter</code> for
	 * transactional updates.
	 * 
	 * @param transaction
	 *            the {@link Transaction}.
	 */
	public DefaultDatastoreWriter(Transaction transaction) {
		this.datastore = transaction.datastore();
		this.nativeWriter = transaction;
	}

	/**
	 * Inserts the given entity into the Cloud Datastore.
	 * 
	 * @param entity
	 *            the entity to insert
	 * @return the inserted entity. The inserted entity will not be same as the
	 *         passed in entity. For example, the inserted entity may contain
	 *         any generated ID, key, parent key, etc.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> E insert(E entity) {
		try {
			FullEntity<?> nativeEntity = (FullEntity<?>) Marshaller.marshal(datastore, entity);
			Entity insertedNativeEntity = nativeWriter.add(nativeEntity);
			@SuppressWarnings("unchecked")
			E insertedEntity = (E) Unmarshaller.unmarshal(insertedNativeEntity, entity.getClass());
			return insertedEntity;
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Inserts the given list of entities into the Cloud Datastore.
	 * 
	 * @param entities
	 *            the entities to insert.
	 * @return the inserted entities. The inserted entities will not be same as
	 *         the passed in entities. For example, the inserted entities may
	 *         contain generated ID, key, parent key, etc.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	@SuppressWarnings("unchecked")
	public <E> List<E> insert(List<E> entities) {
		if (entities == null || entities.isEmpty()) {
			return new ArrayList<E>();
		}
		try {
			FullEntity<?>[] nativeEntities = toNativeFullEntities(entities, datastore);
			Class<?> entityClass = entities.get(0).getClass();
			List<Entity> insertedNativeEntities = nativeWriter.add(nativeEntities);
			return (List<E>) toEntities(entityClass, insertedNativeEntities);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Updates the given entity in the Cloud Datastore. The passed in Entity
	 * must have its ID set for the update to work.
	 * 
	 * @param entity
	 *            the entity to update
	 * @return the updated entity.
	 * @throws EntityManagerException
	 *             if any error occurs while updating.
	 */
	public <E> E update(E entity) {
		try {
			Entity nativeEntity = (Entity) Marshaller.marshal(datastore, entity, true);
			nativeWriter.update(nativeEntity);
			return entity;
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}

	}

	/**
	 * Updates the given entity with optimistic locking, if the entity is set up
	 * to support optimistic locking. Otherwise, a normal update is performed.
	 * 
	 * @param entity
	 *            the entity to update
	 * @return the updated entity which may be different than the given entity.
	 */
	public <E> E updateWithOptimisticLock(E entity) {
		PropertyMetadata versionMetadata = EntityIntrospector.getVersionMetadata(entity);
		if (versionMetadata == null) {
			return update(entity);
		} else {
			return updateWithOptimisticLockingInternal(entity, versionMetadata);
		}

	}

	/**
	 * Worker method for updating the given entity with optimistic locking.
	 * 
	 * @param entity
	 *            the entity to update
	 * @param versionMetadata
	 *            the metadata for optimistic locking
	 * @return the updated entity
	 */
	@SuppressWarnings("unchecked")
	private <E> E updateWithOptimisticLockingInternal(E entity, PropertyMetadata versionMetadata) {
		Transaction transaction = null;
		try {
			Entity nativeEntity = (Entity) Marshaller.marshal(datastore, entity);
			transaction = datastore.newTransaction();
			Entity storedNativeEntity = transaction.get(nativeEntity.key());
			if (storedNativeEntity == null) {
				throw new OptimisticLockException(String.format("Entity does not exist: %s", nativeEntity.key()));
			}
			String versionPropertyName = versionMetadata.getMappedName();
			long version = nativeEntity.getLong(versionPropertyName);
			long storedVersion = storedNativeEntity.getLong(versionPropertyName);
			if (version != storedVersion) {
				throw new OptimisticLockException(
						String.format("Expecting version %d, but found %d", version, storedVersion));
			}
			nativeEntity = incrementVersion(nativeEntity, versionMetadata);
			transaction.update(nativeEntity);
			transaction.commit();
			return (E) Unmarshaller.unmarshal(nativeEntity, entity.getClass());
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		} finally {
			rollbackIfActive(transaction);
		}
	}

	/**
	 * Updates the given list of entities in the Cloud Datastore.
	 * 
	 * @param entities
	 *            the entities to update. The passed in entities must have their
	 *            ID set for the update to work.
	 * @return the updated entities
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> List<E> update(List<E> entities) {
		if (entities == null || entities.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			Entity[] nativeEntities = toNativeEntities(entities, datastore);
			nativeWriter.update(nativeEntities);
			return entities;
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Updates or inserts the given entity in the Cloud Datastore. If the entity
	 * does not have an ID, it may be generated.
	 * 
	 * @param entity
	 *            the entity to update or insert
	 * @return the updated/inserted entity.
	 * @throws EntityManagerException
	 *             if any error occurs while saving.
	 */
	public <E> E upsert(E entity) {
		try {
			FullEntity<?> nativeEntity = (FullEntity<?>) Marshaller.marshal(datastore, entity);
			Entity upsertedNativeEntity = nativeWriter.put(nativeEntity);
			@SuppressWarnings("unchecked")
			E upsertedEntity = (E) Unmarshaller.unmarshal(upsertedNativeEntity, entity.getClass());
			return upsertedEntity;
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Updates or inserts the given list of entities in the Cloud Datastore. If
	 * the entities do not have a valid ID, IDs may be generated.
	 * 
	 * @param entities
	 *            the entities to update/or insert.
	 * @return the updated or inserted entities
	 * @throws EntityManagerException
	 *             if any error occurs while saving.
	 */
	@SuppressWarnings("unchecked")
	public <E> List<E> upsert(List<E> entities) {
		if (entities == null || entities.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			FullEntity<?>[] nativeEntities = toNativeFullEntities(entities, datastore);
			Class<?> entityClass = entities.get(0).getClass();
			List<Entity> upsertedNativeEntities = nativeWriter.put(nativeEntities);
			return (List<E>) toEntities(entityClass, upsertedNativeEntities);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the given entity from the Cloud Datastore.
	 * 
	 * @param entity
	 *            the entity to delete. The entity must have it ID set for the
	 *            deletion to succeed.
	 * @throws EntityManagerException
	 *             if any error occurs while deleting.
	 */
	public void delete(Object entity) {
		try {
			Key nativeKey = Marshaller.marshalKey(datastore, entity);
			nativeWriter.delete(nativeKey);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the given entities from the Cloud Datastore.
	 * 
	 * @param entities
	 *            the entities to delete. The entities must have it ID set for
	 *            the deletion to succeed.
	 * @throws EntityManagerException
	 *             if any error occurs while deleting.
	 */
	public void delete(List<?> entities) {
		try {
			Key[] nativeKeys = new Key[entities.size()];
			for (int i = 0; i < entities.size(); i++) {
				nativeKeys[i] = Marshaller.marshalKey(datastore, entities.get(i));
			}
			nativeWriter.delete(nativeKeys);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes an entity given its key.
	 * 
	 * @param key
	 *            the entity's key
	 * @throws EntityManagerException
	 *             if any error occurs while deleting.
	 */
	public void deleteByKey(DatastoreKey key) {
		try {
			nativeWriter.delete(key.nativeKey());
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the entities having the given keys.
	 * 
	 * @param keys
	 *            the entities' keys
	 * @throws EntityManagerException
	 *             if any error occurs while deleting.
	 */
	public void deleteByKey(List<DatastoreKey> keys) {
		try {
			Key[] nativeKeys = new Key[keys.size()];
			for (int i = 0; i < keys.size(); i++) {
				nativeKeys[i] = keys.get(i).nativeKey();
			}
			nativeWriter.delete(nativeKeys);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the entity with the given ID. The entity is assumed to be a root
	 * entity (no parent). The entity kind will be determined from the supplied
	 * entity class.
	 * 
	 * @param entityClass
	 *            the entity class.
	 * @param id
	 *            the ID of the entity.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> void delete(Class<E> entityClass, long id) {
		try {
			EntityMetadata entityMetadata = EntityIntrospector.introspect(entityClass);
			Key nativeKey = datastore.newKeyFactory().kind(entityMetadata.getKind()).newKey(id);
			nativeWriter.delete(nativeKey);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the entity with the given ID. The entity is assumed to be a root
	 * entity (no parent). The entity kind will be determined from the supplied
	 * entity class.
	 * 
	 * @param entityClass
	 *            the entity class.
	 * @param id
	 *            the ID of the entity.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> void delete(Class<E> entityClass, String id) {
		try {
			EntityMetadata entityMetadata = EntityIntrospector.introspect(entityClass);
			Key nativeKey = datastore.newKeyFactory().kind(entityMetadata.getKind()).newKey(id);
			nativeWriter.delete(nativeKey);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the entity with the given ID and parent key.
	 * 
	 * @param entityClass
	 *            the entity class.
	 * @param parentKey
	 *            the parent key
	 * @param id
	 *            the ID of the entity.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> void delete(Class<E> entityClass, DatastoreKey parentKey, long id) {
		try {
			EntityMetadata entityMetadata = EntityIntrospector.introspect(entityClass);
			Key nativeKey = Key.builder(parentKey.nativeKey(), entityMetadata.getKind(), id).build();
			nativeWriter.delete(nativeKey);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

	/**
	 * Deletes the entity with the given ID and parent key.
	 * 
	 * @param entityClass
	 *            the entity class.
	 * @param parentKey
	 *            the parent key
	 * @param id
	 *            the ID of the entity.
	 * @throws EntityManagerException
	 *             if any error occurs while inserting.
	 */
	public <E> void delete(Class<E> entityClass, DatastoreKey parentKey, String id) {
		try {
			EntityMetadata entityMetadata = EntityIntrospector.introspect(entityClass);
			Key nativeKey = Key.builder(parentKey.nativeKey(), entityMetadata.getKind(), id).build();
			nativeWriter.delete(nativeKey);
		} catch (DatastoreException exp) {
			throw new EntityManagerException(exp);
		}
	}

}