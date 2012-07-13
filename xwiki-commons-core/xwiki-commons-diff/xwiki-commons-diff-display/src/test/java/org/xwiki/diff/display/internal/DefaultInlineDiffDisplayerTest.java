/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.diff.display.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;
import org.xwiki.diff.DiffManager;
import org.xwiki.diff.DiffResult;
import org.xwiki.diff.display.InlineDiffChunk;
import org.xwiki.diff.display.InlineDiffChunk.Type;
import org.xwiki.diff.display.InlineDiffDisplayer;
import org.xwiki.test.AbstractComponentTestCase;

/**
 * Unit tests for {@link DefaultInlineDiffDisplayer}.
 * 
 * @version $Id$
 * @since 4.1M2
 */
public class DefaultInlineDiffDisplayerTest extends AbstractComponentTestCase
{
    @Test
    public void testBothEmpty() throws Exception
    {
        execute("", "", "");
    }

    @Test
    public void testNoChange() throws Exception
    {
        execute("xwiki", "xwiki", "xwiki");
    }

    @Test
    public void testOriginalEmpty() throws Exception
    {
        execute("", "xwiki", "+xwiki+");
    }

    @Test
    public void testRevisedEmpty() throws Exception
    {
        execute("xwiki", "", "-xwiki-");
    }

    @Test
    public void testAddCharacter() throws Exception
    {
        execute("xwki", "xwiki", "xw+i+ki");
    }

    @Test
    public void testRemoveCharacter() throws Exception
    {
        execute("xwiki", "xwki", "xw-i-ki");
    }

    @Test
    public void testChangeCharacter() throws Exception
    {
        execute("xwiki", "xwIki", "xw-i-+I+ki");
    }

    @Test
    public void testEndPoints() throws Exception
    {
        execute("wiki", "xwik", "+x+wik-i-");
    }

    @Test
    public void testAddWord() throws Exception
    {
        execute("123abc", "123XYZabc", "123+XYZ+abc");
    }

    @Test
    public void testRemoveWord() throws Exception
    {
        execute("123xyzABC", "123ABC", "123-xyz-ABC");
    }

    @Test
    public void testChangeWord() throws Exception
    {
        execute("123 xyz abc", "123 XYZ abc", "123 -xyz-+XYZ+ abc");
    }

    @Test
    public void testChangeWords() throws Exception
    {
        execute("123 456 789", "abc 456 xyz", "-123-+abc+ 456 -789-+xyz+");
    }

    /**
     * Builds the in-line diff between the given versions and asserts if the result meets the expectation.
     * 
     * @param previous the previous version
     * @param next the next version
     * @param expected the expected in-line diff
     * @throws Exception if creating the diff fails
     */
    private void execute(String previous, String next, String expected) throws Exception
    {
        List<Character> previousChars = Arrays.asList(ArrayUtils.toObject(previous.toCharArray()));
        List<Character> nextChars = Arrays.asList(ArrayUtils.toObject(next.toCharArray()));

        DiffManager diffManager = getComponentManager().getInstance(DiffManager.class);
        DiffResult<Character> diffResult = diffManager.diff(previousChars, nextChars, null);

        Map<Type, String> separators = new HashMap<Type, String>();
        separators.put(Type.ADDED, "+");
        separators.put(Type.DELETED, "-");
        separators.put(Type.UNMODIFIED, "");

        StringBuilder actual = new StringBuilder();
        InlineDiffDisplayer inlineDiffDisplayer = getComponentManager().getInstance(InlineDiffDisplayer.class);
        for (InlineDiffChunk<Character> chunk : inlineDiffDisplayer.display(diffResult)) {
            String separator = separators.get(chunk.getType());
            actual.append(separator).append(chunk).append(separator);
        }

        Assert.assertEquals(expected, actual.toString());
    }
}
