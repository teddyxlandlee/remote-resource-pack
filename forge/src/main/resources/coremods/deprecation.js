var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI')
var Opcodes = Java.type('org.objectweb.asm.Opcodes')
var InsnList = Java.type('org.objectweb.asm.tree.InsnList')
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode')

function initializeCoreMod() {
	return {
		'rrp_lexforge_deprecation': {
			'target': {
				'type': 'METHOD',
				'class': 'net.minecraft.client.Minecraft',
				'methodName': 'm_295067_',
				'methodDesc': '(Ljava/util/List;)V'
			},
			'transformer': function (method) {
				var listSupplier = function () {
					var list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 1));    // the list
					list.add(ASMAPI.buildMethodCall(
							"xland/mcmod/remoteresourcepack/forge/NeoMigrationWarningScreen",
							"addWarningScreen",
							"(Ljava/util/List;)V",
							ASMAPI.MethodType.STATIC
					));
					return list;
				};

				var itr = method.instructions.iterator();
				while (itr.hasNext()) {
					var ins = itr.next();
					if (ins.getOpcode() == Opcodes.RETURN) {
						method.instructions.insertBefore(ins, listSupplier());
					}
				}

				return method;
			}
		}
	};
}
