/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.processor.antlr.generated; // Generated from JavadocParser.g4 by ANTLR 4.13.0

import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagContentContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagNameContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagTextContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagTextElementContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BraceContentContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BraceExpressionContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BraceTextContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionLineContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionLineElementContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionLineNoSpaceNoAtContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionLineStartContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionLineTextContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DescriptionNewlineContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DocumentationContentContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DocumentationContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.InlineTagContentContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.InlineTagContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.InlineTagNameContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.SkipWhitespaceContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.TagSectionContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * This class provides an empty implementation of {@link JavadocParserListener}, which can be extended to create a
 * listener which only needs to handle a subset of the available methods.
 */
@SuppressWarnings("CheckReturnValue")
public class JavadocParserBaseListener implements JavadocParserListener {
    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDocumentation(DocumentationContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDocumentation(DocumentationContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDocumentationContent(DocumentationContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDocumentationContent(DocumentationContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterSkipWhitespace(SkipWhitespaceContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitSkipWhitespace(SkipWhitespaceContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescription(DescriptionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescription(DescriptionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionLine(DescriptionLineContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionLine(DescriptionLineContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionLineStart(DescriptionLineStartContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionLineStart(DescriptionLineStartContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionLineNoSpaceNoAt(DescriptionLineNoSpaceNoAtContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionLineNoSpaceNoAt(DescriptionLineNoSpaceNoAtContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionLineElement(DescriptionLineElementContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionLineElement(DescriptionLineElementContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionLineText(DescriptionLineTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionLineText(DescriptionLineTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterDescriptionNewline(DescriptionNewlineContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitDescriptionNewline(DescriptionNewlineContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterTagSection(TagSectionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitTagSection(TagSectionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBlockTag(BlockTagContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBlockTag(BlockTagContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBlockTagName(BlockTagNameContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBlockTagName(BlockTagNameContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBlockTagContent(BlockTagContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBlockTagContent(BlockTagContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBlockTagText(BlockTagTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBlockTagText(BlockTagTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBlockTagTextElement(BlockTagTextElementContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBlockTagTextElement(BlockTagTextElementContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInlineTag(InlineTagContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInlineTag(InlineTagContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInlineTagName(InlineTagNameContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInlineTagName(InlineTagNameContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterInlineTagContent(InlineTagContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitInlineTagContent(InlineTagContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBraceExpression(BraceExpressionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBraceExpression(BraceExpressionContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBraceContent(BraceContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBraceContent(BraceContentContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterBraceText(BraceTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitBraceText(BraceTextContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void enterEveryRule(ParserRuleContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void exitEveryRule(ParserRuleContext ctx) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void visitTerminal(TerminalNode node) {}

    /**
     * {@inheritDoc}
     *
     * <p>The default implementation does nothing.</p>
     */
    @Override
    public void visitErrorNode(ErrorNode node) {}
}
