package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.*

class PythonVisualTransformation(
    val errors: List<CodeError>
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedString {
                val str = text.text
                append(str)
                
                // Simple keyword highlighting
                val keywords = listOf("def ", "class ", "import ", "from ", "return ", "if ", "else:", "elif ", "try:", "except", "pass", "True", "False", "None", "self")
                keywords.forEach { keyword -> 
                    var index = str.indexOf(keyword)
                    while (index >= 0) {
                        addStyle(SpanStyle(color = SyntaxFunction), index, index + keyword.length)
                        index = str.indexOf(keyword, index + 1)
                    }
                }
                
                // Highlight known errors
                errors.forEach { error ->
                    if (error.start >= 0 && error.end <= str.length) {
                        addStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = Color.Red,
                                background = Color.Red.copy(alpha = 0.2f)
                            ), 
                            error.start, 
                            error.end
                        )
                    }
                }
            },
            OffsetMapping.Identity
        )
    }
}

data class CodeError(val word: String, val suggestion: String, val start: Int, val end: Int)

fun detectErrors(code: String): List<CodeError> {
    val errors = mutableListOf<CodeError>()
    
    // Example typo: prnt -> print
    val prntRegex = Regex("\\bprnt\\b")
    prntRegex.findAll(code).forEach { match ->
        errors.add(CodeError("prnt", "print", match.range.first, match.range.last + 1))
    }
    
    // Example typo: tpye -> type
    val tpyeRegex = Regex("\\btpye\\b")
    tpyeRegex.findAll(code).forEach { match ->
        errors.add(CodeError("tpye", "type", match.range.first, match.range.last + 1))
    }

    // Example typo: strnig -> string
    val strnigRegex = Regex("\\bstrnig\\b")
    strnigRegex.findAll(code).forEach { match ->
        errors.add(CodeError("strnig", "str", match.range.first, match.range.last + 1))
    }

    return errors
}
