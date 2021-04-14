## 私有字段提案

Lox 的 class 中的属性字段是不需要声明的，直接在 `init` 函数中使用即可。

```typescript
class Data {
    init(value) {
        this.someField = value
    }
}
```

那么私有字段要如何和公共字段区分开呢？最简单的无疑是：

```typescript 
class Data {
    private pField;
    init(value) {
        this.someField = value;
    }
}
```

这样就是使得私有需要声明，公共的不用声明。有行为不一致的问题。

在 `Python` 中，如果你使用的字段名是 `__` 开头的，那么它就认为这个字段是私有的。但是这样也使得私有字段普遍没公共字段「好看」。

所以为了统一性，希望在 `class` 层面统一声明字段，并且无法通过 `.` 直接新增字段。

```typescript
class Data {
    someField;
    private pField;

    init(value) {
        this.someField = value;

        // this will throw error
        this.otherFieldNotDeclare = value;
    }
}
```