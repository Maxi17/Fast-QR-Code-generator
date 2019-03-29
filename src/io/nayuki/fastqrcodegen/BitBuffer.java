/* 
 * Fast QR Code generator library
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/fast-qr-code-generator-library
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.nayuki.fastqrcodegen;

import org.checkerframework.checker.index.qual.IndexFor;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.common.value.qual.IntRange;

import java.util.Arrays;
import java.util.Objects;


/**
 * An appendable sequence of bits (0s and 1s). Mainly used by {@link QrSegment}.
 */
final class BitBuffer {
	
	/*---- Fields ----*/

	int[] data;
	
	@NonNegative int bitLength;
	
	
	
	/*---- Constructor ----*/
	
	/**
	 * Constructs an empty bit buffer (length 0).
	 */
	public BitBuffer() {
		data = new int[64];
		bitLength = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the length of this sequence, which is a non-negative value.
	 * @return the length of this sequence
	 */
	@SuppressWarnings("index")
	// There is one error related to an attempt to access an array using a bitwise operation on bitLength.
	// Because bitLength >>> 5 has the same effect as bitLength /= 32, we can safely
	// assume that if bitLength is non negative, then so is bitLength / 32.
	public int getBit(@NonNegative int index) {
		if (index < 0 || index >= bitLength)
			throw new IndexOutOfBoundsException();
		return (data[index >>> 5] >>> ~index) & 1;
	}
	
	
	/**
	 * Returns an array representing this buffer's bits packed into bytes
	 * in big endian. The current bit length must be a multiple of 8.
	 * @return a new byte array (not {@code null}) representing this bit sequence
	 */
	@SuppressWarnings("index")
	// Again, the error is issued by the attempt of accessing the data array with an index that is obtained after a
	// bitwise operation. Since i >>> 2 has the same effect as i /= 4, it stays non-negative and smaller than result.length
	public byte[] getBytes() {
		if (bitLength % 8 != 0)
			throw new IllegalStateException("Data is not a whole number of bytes");
		byte[] result = new byte[bitLength / 8];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)(data[i >>> 2] >>> (~i << 3));
		return result;
	}
	
	
	/**
	 * Appends the specified number of low-order bits of the specified value to this
	 * buffer. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
	 * @param val the value to append
	 * @param len the number of low-order bits in the value to take
	 * @throws IllegalArgumentException if the value or number of bits is out of range
	 * @throws IllegalStateException if appending the data
	 * would make bitLength exceed Integer.MAX_VALUE
	 */
	@SuppressWarnings({"index","compound"})
	// There are 2 errors below, both related to the attempt to access data array using a
	// bitwise operation on bitLength. Because bitLength >>> 5 has the same effect as bitLength / 32, we can safely
	// assume that if bitLength is non negative, then so is bitLength / 32.
	public void appendBits(int val, @IntRange(from = 0, to = 31) int len) {
		if (len < 0 || len > 31 || val >>> len != 0)
			throw new IllegalArgumentException("Value out of range");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		if (bitLength + len + 1 > data.length << 5)
			data = Arrays.copyOf(data, data.length * 2);
		assert bitLength + len <= data.length << 5;
		int remain = 32 - (bitLength & 0x1F);
		assert 1 <= remain && remain <= 32;
		if (remain < len) {
			data[bitLength >>> 5] |= val >>> (len - remain);
			bitLength += remain;
			assert (bitLength & 0x1F) == 0;
			len -= remain;
			val &= (1 << len) - 1;
			remain = 32;
		}
		data[bitLength >>> 5] |= val << (remain - len);
		bitLength += len;
	}
	
	
	/**
	 * Appends the specified sequence of bits to this buffer.
	 * Requires 0 &#x2264; len &#x2264; 32 &#xD7; vals.length.
	 * @param vals the sequence of bits to append (not {@code null})
	 * @param len the number of prefix bits to read from the array
	 * @throws IllegalStateException if appending the data
	 * would make bitLength exceed Integer.MAX_VALUE
	 */
	@SuppressWarnings("index")
	// The reason why bitLength >>> 5 is safe has been explained at the above method.
	// Another warning suppressed here was related to the call of System.arraycopy. The call is safe because 0 is an index
	// of vals, bitLength / 32 is an index of data and (len + 31) / 32 is correct as it was checked in the if statements
	// above it. These are len > vals.length * 32L and len > Integer.MAX_VALUE - bitLength.
	public void appendBits(int[] vals, @NonNegative int len) {
		Objects.requireNonNull(vals);
		if (len == 0)
			return;
		if (len < 0 || len > vals.length * 32L)
			throw new IllegalArgumentException("Value out of range");
		@IndexFor("#1") int wholeWords = len / 32;
		int tailBits = len % 32;
		if (tailBits > 0 && vals[wholeWords] << tailBits != 0)
			throw new IllegalArgumentException("Last word must have low bits clear");
		if (len > Integer.MAX_VALUE - bitLength)
			throw new IllegalStateException("Maximum length reached");
		
		while (bitLength + len > data.length * 32)
			data = Arrays.copyOf(data, data.length * 2);

		int shift = bitLength % 32;
		if (shift == 0) {
			System.arraycopy(vals, 0, data, bitLength / 32, (len + 31) / 32);
			bitLength += len;
		} else {
			for (int i = 0; i < wholeWords; i++) {
				int word = vals[i];
				data[bitLength >>> 5] |= word >>> shift;
				bitLength += 32;
				data[bitLength >>> 5] = word << (32 - shift);
			}
			if (tailBits > 0)
				appendBits(vals[wholeWords] >>> (32 - tailBits), tailBits);
		}
	}
	
}
