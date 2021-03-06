package be.tjoener.klox.parser

import be.tjoener.klox.KLox
import be.tjoener.klox.ast.*
import be.tjoener.klox.ast.Function
import be.tjoener.klox.ast.Set
import be.tjoener.klox.parser.TokenType.*


class Parser(private val tokens: List<Token>) {

    class ParseError : RuntimeException()

    private var current = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            val declaration = declaration()
            if (declaration != null) {
                statements.add(declaration)
            }
        }

        return statements
    }

    private fun declaration(): Stmt? {
        try {
            if (match(CLASS)) return classDeclaration()
            if (match(FUN)) return function("function")
            if (match(VAR)) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect class name")

        val superclass = if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name")
            Variable(previous())
        } else null

        consume(LEFT_BRACE, "Expect '{' before class body")

        val methods = mutableListOf<Function>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(RIGHT_BRACE, "Expect '}' after class body")
        return Class(name, superclass, methods)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name")

        val initializer = if (match(EQUAL)) {
            expression()
        } else null

        consume(SEMICOLON, "Expect ';' after variable declaration")
        return Var(name, initializer)
    }

    private fun function(kind: String): Function {
        val name = consume(IDENTIFIER, "Expect $kind name")
        consume(LEFT_PAREN, "Expect '(' after $kind name")

        val parameters = mutableListOf<Token>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 8) {
                    error(peek(), "Cannot have more than 8 parameters")
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name"))
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters")

        consume(LEFT_BRACE, "Expect '{' before $kind body")
        val body = block()

        return Function(name, parameters, body)
    }

    private fun statement(): Stmt {
        if (match(FOR)) return forStatement()
        if (match(IF)) return ifStatement()
        if (match(PRINT)) return printStatement()
        if (match(RETURN)) return returnStatement()
        if (match(WHILE)) return whileStatement()
        if (match(LEFT_BRACE)) return Block(block())
        return expressionStatement()
    }

    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'for'")

        val initializer: Stmt? = when {
            match(SEMICOLON) -> null
            match(VAR) -> varDeclaration()
            else -> expressionStatement()
        }

        var condition: Expr? =
            if (!check(SEMICOLON)) expression()
            else null
        consume(SEMICOLON, "Expect ';' after loop condition")

        val increment: Expr? =
            if (!check(RIGHT_PAREN)) expression()
            else null
        consume(RIGHT_PAREN, "Expect ')' after for clauses")

        var body = statement()
        if (increment != null) {
            body = Block(listOf(body, Expression(increment)))
        }

        if (condition == null) condition = Literal(true)
        body = While(condition, body)

        if (initializer != null) {
            body = Block(listOf(initializer, body))
        }

        return body
    }

    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after if condition")

        val thenBranch = statement()
        val elseBranch = if (match(ELSE)) statement() else null

        return If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value")
        return Print(value)
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value = if (!check(SEMICOLON)) expression() else null

        consume(SEMICOLON, "Expect ';' after return value")
        return Return(keyword, value)
    }

    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after condition")
        val body = statement()

        return While(condition, body)
    }

    private fun expressionStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after expression")
        return Expression(value)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            val declaration = declaration()
            if (declaration != null) {
                statements.add(declaration)
            }
        }

        consume(RIGHT_BRACE, "Expect '}' after block")
        return statements
    }


    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = or()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Variable) {
                return Assign(expr.name, value)
            } else if (expr is Get) {
                return Set(expr.obj, expr.name, value)
            }

            error(equals, "Invalid assignment target")
        }

        return expr
    }

    private fun or(): Expr {
        var expr = and()

        while (match(OR)) {
            val operator = previous()
            val right = and()
            expr = Logical(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Logical(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(EQUAL_EQUAL, BANG_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = addition()

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val operator = previous()
            val right = addition()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun addition(): Expr {
        var expr = multiplication()

        while (match(PLUS, MINUS)) {
            val operator = previous()
            val right = multiplication()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun multiplication(): Expr {
        var expr = unary()

        while (match(SLASH, STAR)) {
            val operator = previous()
            val right = unary()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(DOT)) {
                val name = consume(IDENTIFIER, "Expect property name after '.'")
                expr = Get(expr, name)
            } else {
                break
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 8) {
                    error(peek(), "Cannot have more than 8 arguments")
                }
                arguments.add(expression())
            } while (match(COMMA))
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments")
        return Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(FALSE)) return Literal(false)
        if (match(TRUE)) return Literal(true)
        if (match(NIL)) return Literal(null)

        if (match(NUMBER)) return Literal(previous().literal)
        if (match(STRING)) return Literal(previous().literal)

        if (match(SUPER)) {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'")
            val method = consume(IDENTIFIER, "Expect superclass method name")
            return Super(keyword, method)
        }

        if (match(THIS)) return This(previous())

        if (match(IDENTIFIER)) return Variable(previous())

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression")
            return Grouping(expr)
        }

        throw error(peek(), "Expect expression")
    }

    private fun match(vararg types: TokenType): Boolean {
        val matches = types.any { check(it) }
        if (matches) advance()
        return matches
    }

    private fun check(tokenType: TokenType): Boolean {
        return if (isAtEnd()) false else peek().type == tokenType
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type == EOF
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): ParseError {
        KLox.error(token, message)
        throw ParseError()
    }

    private fun synchronize() {
        val boundaries = listOf(CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN)

        advance()
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return
            if (peek().type in boundaries) return
            advance()
        }
    }

}
