package xland.mcmod.remoteresourcepack.fabric.asm;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ApiStatus.Internal
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface TransformTarget {
}
