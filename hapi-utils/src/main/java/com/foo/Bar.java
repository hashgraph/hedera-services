package com.foo;

import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotationForMethods(NonNull.class)
public class Bar {

	public String yeah() {
		return "check";
	}

	public String ohNo() {
		return null;
	}
}
