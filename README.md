# Programming Language Compiler

A lightweight programming language compiler and interpreter implemented in Java. This project processes a custom programming language, providing lexical analysis, parsing, semantic checking, and execution functionalities. It translates input code into an Abstract Syntax Tree (AST), validates semantics, and either interprets the AST or generates executable code.

## Features

### 1. **Lexical Analysis (Lexer)**
   - Tokenizes input source code into meaningful units (tokens), such as identifiers, operators, numbers, and keywords.
   - Handles errors like invalid characters or malformed numbers.

### 2. **Parsing (Parser)**
   - Converts the tokens into an Abstract Syntax Tree (AST).
   - Implements grammar rules for the language to ensure syntactic correctness.

### 3. **Semantic Analysis (Analyzer)**
   - Checks the validity of types, variable declarations, and function definitions.
   - Ensures variables are used within scope and adheres to constant/mutable rules.

### 4. **AST Representation**
   - Represents the structure of the source code using nodes for fields, methods, statements, and expressions.

### 5. **Code Generation (Generator)**
   - Translates the AST into Java syntax or other target executable code.

### 6. **Interpreter**
   - Directly executes the AST by interpreting its semantics.
   - Handles control flow (e.g., `if`, `for`, `while`) and arithmetic/logical operations.

### 7. **Scope and Environment Management**
   - Manages variable and function declarations, including types, values, and scope resolution.

## Directory Structure
```
P4
├── src
│   ├── Analyzer.java
│   ├── Ast.java
│   ├── Environment.java
│   ├── Generator.java
│   ├── Interpreter.java
│   ├── Lexer.java
│   ├── Parser.java
│   ├── Scope.java
│   └── Token.java
└── README.md
```

## Getting Started

### Prerequisites
- **Java Development Kit (JDK)**: Version 8 or higher.
- **Git**: Version control for cloning the repository.

### Clone the Repository
```bash
git clone https://github.com/Retsamic/Programming-Language-Compiler.git
cd Programming-Language-Compiler
```

### Build and Run
1. Compile the Java files:
   ```bash
   javac src/*.java
   ```
2. Execute the main program (if a specific entry point exists):
   ```bash
   java src.Main
   ```

### Example Usage
1. Provide a source code file in the custom language as input.
2. The Lexer generates tokens from the input.
3. The Parser builds an AST.
4. The Analyzer checks semantic correctness.
5. The Generator or Interpreter processes the AST for execution or code generation.

## Technologies Used
- **Java**: Core programming language for development.
- **Git**: Version control.

## Contributing
Contributions are welcome! Follow these steps to contribute:
1. Fork the repository.
2. Create a new feature branch.
3. Commit changes and push the branch.
4. Submit a pull request.
