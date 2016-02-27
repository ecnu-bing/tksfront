package common;

import java.util.Comparator;

/**
 * motivated by
 * http://stackoverflow.com/questions/4859261/get-the-indices-of-an-array-after-
 * sorting
 * 
 * @author xiafan
 *
 * @param <T>
 */
public class ArrayIndexComparator<T extends Comparable<T>> implements Comparator<Integer> {
	private final T[] array;

	public ArrayIndexComparator(T[] array) {
		this.array = array;
	}

	public Integer[] createIndexArray() {
		Integer[] indexes = new Integer[array.length];
		for (int i = 0; i < array.length; i++) {
			indexes[i] = i;
		}
		return indexes;
	}

	@Override
	public int compare(Integer index1, Integer index2) {
		if (array[index1] == null) {
			return 1;
		} else if (array[index2] == null) {
			return -1;
		}
		return array[index1].compareTo(array[index2]);
	}

}
