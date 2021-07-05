/*
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2003 Nick Lothian. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        developers of Classifier4J (http://classifier4j.sf.net/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The name "Classifier4J" must not be used to endorse or promote
 *    products derived from this software without prior written
 *    permission. For written permission, please contact
 *    http://sourceforge.net/users/nicklothian/.
 *
 * 5. Products derived from this software may not be called
 *    "Classifier4J", nor may "Classifier4J" appear in their names
 *    without prior written permission. For written permission, please
 *    contact http://sourceforge.net/users/nicklothian/.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 */

package net.sf.classifier4J;

/**
 * <p>Very basic implemntation of the {@link net.sf.classifier4J.IClassifier} interface.</p>
 *
 * <p>This implementation just looks for string (set by {@link #setSearchWord(java.lang.String)})
 * in the input passed to  {@link #classify(java.lang.String)}</p>
 *
 * @author Nick Lothian
 */
public class SimpleClassifier extends AbstractClassifier implements IClassifier {

    private String searchWord;

    /**
     * @return the word this classifier is searching for
     */
    public String getSearchWord() {
        return searchWord;
    }

    /**
     * @param string The string to look for when matching
     */
    public void setSearchWord(String string) {
        searchWord = string;
    }

    /**
     * @see net.sf.classifier4J.IClassifier#classify(java.lang.String)
     */
    public double classify(String input) {
        if ((input != null) && (input.indexOf(searchWord) > 0)) {
            return 1;
        } else {
            return 0;
        }
    }

}
