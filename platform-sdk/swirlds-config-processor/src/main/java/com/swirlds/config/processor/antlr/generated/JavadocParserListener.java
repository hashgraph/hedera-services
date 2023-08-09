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
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by {@link JavadocParser}.
 */
public interface JavadocParserListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link JavadocParser#documentation}.
     *
     * @param ctx the parse tree
     */
    void enterDocumentation(DocumentationContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#documentation}.
     *
     * @param ctx the parse tree
     */
    void exitDocumentation(DocumentationContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#documentationContent}.
     *
     * @param ctx the parse tree
     */
    void enterDocumentationContent(DocumentationContentContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#documentationContent}.
     *
     * @param ctx the parse tree
     */
    void exitDocumentationContent(DocumentationContentContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#skipWhitespace}.
     *
     * @param ctx the parse tree
     */
    void enterSkipWhitespace(SkipWhitespaceContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#skipWhitespace}.
     *
     * @param ctx the parse tree
     */
    void exitSkipWhitespace(SkipWhitespaceContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#description}.
     *
     * @param ctx the parse tree
     */
    void enterDescription(DescriptionContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#description}.
     *
     * @param ctx the parse tree
     */
    void exitDescription(DescriptionContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionLine}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionLine(DescriptionLineContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionLine}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionLine(DescriptionLineContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionLineStart}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionLineStart(DescriptionLineStartContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionLineStart}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionLineStart(DescriptionLineStartContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionLineNoSpaceNoAt}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionLineNoSpaceNoAt(DescriptionLineNoSpaceNoAtContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionLineNoSpaceNoAt}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionLineNoSpaceNoAt(DescriptionLineNoSpaceNoAtContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionLineElement}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionLineElement(DescriptionLineElementContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionLineElement}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionLineElement(DescriptionLineElementContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionLineText}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionLineText(DescriptionLineTextContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionLineText}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionLineText(DescriptionLineTextContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#descriptionNewline}.
     *
     * @param ctx the parse tree
     */
    void enterDescriptionNewline(DescriptionNewlineContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#descriptionNewline}.
     *
     * @param ctx the parse tree
     */
    void exitDescriptionNewline(DescriptionNewlineContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#tagSection}.
     *
     * @param ctx the parse tree
     */
    void enterTagSection(TagSectionContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#tagSection}.
     *
     * @param ctx the parse tree
     */
    void exitTagSection(TagSectionContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#blockTag}.
     *
     * @param ctx the parse tree
     */
    void enterBlockTag(BlockTagContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#blockTag}.
     *
     * @param ctx the parse tree
     */
    void exitBlockTag(BlockTagContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#blockTagName}.
     *
     * @param ctx the parse tree
     */
    void enterBlockTagName(BlockTagNameContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#blockTagName}.
     *
     * @param ctx the parse tree
     */
    void exitBlockTagName(BlockTagNameContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#blockTagContent}.
     *
     * @param ctx the parse tree
     */
    void enterBlockTagContent(BlockTagContentContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#blockTagContent}.
     *
     * @param ctx the parse tree
     */
    void exitBlockTagContent(BlockTagContentContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#blockTagText}.
     *
     * @param ctx the parse tree
     */
    void enterBlockTagText(BlockTagTextContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#blockTagText}.
     *
     * @param ctx the parse tree
     */
    void exitBlockTagText(BlockTagTextContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#blockTagTextElement}.
     *
     * @param ctx the parse tree
     */
    void enterBlockTagTextElement(BlockTagTextElementContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#blockTagTextElement}.
     *
     * @param ctx the parse tree
     */
    void exitBlockTagTextElement(BlockTagTextElementContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#inlineTag}.
     *
     * @param ctx the parse tree
     */
    void enterInlineTag(InlineTagContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#inlineTag}.
     *
     * @param ctx the parse tree
     */
    void exitInlineTag(InlineTagContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#inlineTagName}.
     *
     * @param ctx the parse tree
     */
    void enterInlineTagName(InlineTagNameContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#inlineTagName}.
     *
     * @param ctx the parse tree
     */
    void exitInlineTagName(InlineTagNameContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#inlineTagContent}.
     *
     * @param ctx the parse tree
     */
    void enterInlineTagContent(InlineTagContentContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#inlineTagContent}.
     *
     * @param ctx the parse tree
     */
    void exitInlineTagContent(InlineTagContentContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#braceExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBraceExpression(BraceExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#braceExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBraceExpression(BraceExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#braceContent}.
     *
     * @param ctx the parse tree
     */
    void enterBraceContent(BraceContentContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#braceContent}.
     *
     * @param ctx the parse tree
     */
    void exitBraceContent(BraceContentContext ctx);

    /**
     * Enter a parse tree produced by {@link JavadocParser#braceText}.
     *
     * @param ctx the parse tree
     */
    void enterBraceText(BraceTextContext ctx);

    /**
     * Exit a parse tree produced by {@link JavadocParser#braceText}.
     *
     * @param ctx the parse tree
     */
    void exitBraceText(BraceTextContext ctx);
}
