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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.cloud.datastore.ListValue;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.Value;
import com.google.cloud.datastore.ValueBuilder;

/**
 * An implementation of {@link PropertyConverter} for handling List types.
 * 
 * @author Sai Pullabhotla
 *
 */
public class ListConverter extends AbstractConverter {

	/**
	 * Singleton instance
	 */
	private static final ListConverter INSTANCE = new ListConverter();

	/**
	 * String Converter
	 */
	private static final PropertyConverter stringConverter = StringConverter.getInstance();

	/**
	 * Long Converter
	 */
	private static final PropertyConverter longConverter = LongConverter.getInstance();

	/**
	 * Creates a new instance of <code>ListConverter</code>.
	 */
	private ListConverter() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public ValueBuilder<?, ?, ?> toValueBuilder(Object obj) {
		List<?> list = (List<?>) obj;
		Iterator<?> iterator = list.iterator();
		ListValue.Builder listValurBuilder = ListValue.builder();
		while (iterator.hasNext()) {
			Object item = iterator.next();
			PropertyConverter converter;
			if (item instanceof String) {
				converter = stringConverter;
			} else if (item instanceof Long) {
				converter = longConverter;
			} else {
				throw new RuntimeException("Unsupported type in List");
			}
			Value<?> convertedItem = converter.toValue(item);
			listValurBuilder.addValue(convertedItem);
		}
		return listValurBuilder;

	}

	@Override
	public Object toObject(Value<?> value) {
		ListValue listValue = (ListValue) value;
		List<? extends Value<?>> list = listValue.get();
		Iterator<? extends Value<?>> iterator = list.iterator();
		List<Object> output = new ArrayList<>(list.size());
		PropertyConverter converter;
		while (iterator.hasNext()) {
			Value<?> item = iterator.next();
			if (item instanceof StringValue) {
				converter = stringConverter;
			} else if (item instanceof LongValue) {
				converter = longConverter;
			} else {
				throw new RuntimeException("Unsupported type in list");
			}
			Object convertedItem = converter.toObject(item);
			output.add(convertedItem);
		}
		return output;
	}

	/**
	 * Returns the singleton instance of <code>ListConverter</code>.
	 * 
	 * @return the singleton instance of <code>ListConverter</code>.
	 */
	public static ListConverter getInstance() {
		return INSTANCE;
	}

}