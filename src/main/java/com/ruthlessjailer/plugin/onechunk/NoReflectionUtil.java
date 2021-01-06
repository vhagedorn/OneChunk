package com.ruthlessjailer.plugin.onechunk;

import com.nesaak.noreflection.access.DynamicCaller;

/**
 * @author RuthlessJailer
 */
public final class NoReflectionUtil {

	public static void setMethodArrayOutput(final int index, final Object value, final DynamicCaller caller, final Object... objects) {
		((Object[]) caller.call(objects))[index] = value;
	}

}
