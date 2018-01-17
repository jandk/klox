package be.tjoener.klox.ast

import be.tjoener.klox.parser.Token

sealed class Stmt {
    abstract fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<out R> {
        fun visitBlockStmt(stmt: Block): R
        fun visitClassStmt(stmt: Class): R
        fun visitExpressionStmt(stmt: Expression): R
        fun visitFunctionStmt(stmt: Function): R
        fun visitIfStmt(stmt: If): R
        fun visitPrintStmt(stmt: Print): R
        fun visitReturnStmt(stmt: Return): R
        fun visitVarStmt(stmt: Var): R
        fun visitWhileStmt(stmt: While): R
    }
}

class Block(val statements: List<Stmt>) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitBlockStmt(this)
    }
}

class Class(val name: Token, val methods: List<Function>) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitClassStmt(this)
    }
}

class Expression(val expression: Expr) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitExpressionStmt(this)
    }
}

class Function(val name: Token, val parameters: List<Token>, val body: List<Stmt>) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitFunctionStmt(this)
    }
}

class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitIfStmt(this)
    }
}

class Print(val expression: Expr) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitPrintStmt(this)
    }
}

class Return(val keyword: Token, val value: Expr?) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitReturnStmt(this)
    }
}

class Var(val name: Token, val initializer: Expr?) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitVarStmt(this)
    }
}

class While(val condition: Expr, val body: Stmt) : Stmt() {
    override fun <R> accept(visitor: Visitor<R>): R {
        return visitor.visitWhileStmt(this)
    }
}