package xxliam.cookieclient.render.shader;

import org.lwjgl.opengl.GL20;

/**
 * shader uniform 基类。
 */
public abstract class Uniform<T extends Uniform<?>> {

    private final String name;
    private int programId;
    private int location;

    public Uniform(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public T bindToProgram(int programId) {
        this.programId = programId;
        this.location = GL20.glGetUniformLocation(this.programId, this.name);
        return (T) this;
    }

    public String getName() {
        return name;
    }

    public int getProgramId() {
        return programId;
    }

    public int getLocation() {
        return location;
    }
}
