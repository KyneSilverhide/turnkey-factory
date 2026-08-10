#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Nettoie l'export de schematic de la maison de départ.

L'export vient d'un monde chargé de mods : il embarque des blocs et des données qui
n'existent pas dans notre environnement, et que Minecraft remplace SILENCIEUSEMENT par de
l'air au chargement (`NbtUtils.readBlockState` renvoie AIR pour un bloc inconnu — aucune
erreur, aucun log). Ce script coupe tout ce qui n'est pas vanilla :

  * la palette   : `visualworkbench:minecraft/crafting_table` -> `minecraft:crafting_table` ;
  * les entités  : l'unique porte-armure traîne des Ko de NBT Accessories/Curios/Caelus ;
  * les block entities : `neoforge:attachments` (encre antique de Supplementaries) sur le
    panneau, et le `nbt` de l'établi moddé (une table de craft vanilla n'a pas de BE) ;
  * la racine    : `Railways_DataVersion`, ajouté par Create: Steam 'n Rails.

`DataVersion` (3955 = 1.21.1) est en revanche CONSERVÉ : sans lui, `DataFixTypes.STRUCTURE`
considère le fichier comme une version 500 et déroule toute la chaîne de correctifs sur la
palette. Avec lui, le correcteur ne fait rien.

Le script est idempotent : relancé sur sa propre sortie, il ne change rien. Usage :

    python tools/sanitize_starter_house.py [export.nbt]

Sans argument il retraite le fichier déjà installé dans les ressources ; avec un argument il
prend un nouvel export (typiquement `starter_house.nbt` fraîchement sorti du jeu) et écrit
toujours dans les ressources.
"""

import gzip
import os
import struct
import sys

# ----- Lecture/écriture NBT, en conservant les types -----
#
# Un parseur générique qui renverrait des valeurs Python nues perdrait de quoi réécrire le
# fichier à l'identique (TAG_Int vs TAG_Short, type d'élément d'une liste vide...). On
# transporte donc partout le couple (id de tag, charge utile).

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class Reader(object):
    def __init__(self, data):
        self.data = data
        self.i = 0

    def raw(self, n):
        v = self.data[self.i:self.i + n]
        self.i += n
        return v

    def unpack(self, fmt, n):
        v = struct.unpack_from(fmt, self.data, self.i)[0]
        self.i += n
        return v

    def string(self):
        n = self.unpack('>H', 2)
        return self.raw(n).decode('utf-8')

    def payload(self, tid):
        if tid == TAG_BYTE:
            return self.unpack('>b', 1)
        if tid == TAG_SHORT:
            return self.unpack('>h', 2)
        if tid == TAG_INT:
            return self.unpack('>i', 4)
        if tid == TAG_LONG:
            return self.unpack('>q', 8)
        if tid == TAG_FLOAT:
            return self.unpack('>f', 4)
        if tid == TAG_DOUBLE:
            return self.unpack('>d', 8)
        if tid == TAG_BYTE_ARRAY:
            return list(bytearray(self.raw(self.unpack('>i', 4))))
        if tid == TAG_STRING:
            return self.string()
        if tid == TAG_LIST:
            el = self.unpack('>b', 1)
            n = self.unpack('>i', 4)
            return (el, [self.payload(el) for _ in range(n)])
        if tid == TAG_COMPOUND:
            out = {}
            while True:
                child = self.unpack('>b', 1)
                if child == TAG_END:
                    return out
                name = self.string()
                out[name] = (child, self.payload(child))
        if tid == TAG_INT_ARRAY:
            return [self.unpack('>i', 4) for _ in range(self.unpack('>i', 4))]
        if tid == TAG_LONG_ARRAY:
            return [self.unpack('>q', 8) for _ in range(self.unpack('>i', 4))]
        raise ValueError('tag inconnu: %d' % tid)


class Writer(object):
    def __init__(self):
        self.out = bytearray()

    def pack(self, fmt, v):
        self.out += struct.pack(fmt, v)

    def string(self, s):
        b = s.encode('utf-8')
        self.pack('>H', len(b))
        self.out += b

    def payload(self, tid, v):
        if tid == TAG_BYTE:
            self.pack('>b', v)
        elif tid == TAG_SHORT:
            self.pack('>h', v)
        elif tid == TAG_INT:
            self.pack('>i', v)
        elif tid == TAG_LONG:
            self.pack('>q', v)
        elif tid == TAG_FLOAT:
            self.pack('>f', v)
        elif tid == TAG_DOUBLE:
            self.pack('>d', v)
        elif tid == TAG_BYTE_ARRAY:
            self.pack('>i', len(v))
            self.out += bytearray([x & 0xFF for x in v])
        elif tid == TAG_STRING:
            self.string(v)
        elif tid == TAG_LIST:
            el, items = v
            self.pack('>b', el)
            self.pack('>i', len(items))
            for item in items:
                self.payload(el, item)
        elif tid == TAG_COMPOUND:
            for name, (child, val) in v.items():
                self.pack('>b', child)
                self.string(name)
                self.payload(child, val)
            self.pack('>b', TAG_END)
        elif tid == TAG_INT_ARRAY:
            self.pack('>i', len(v))
            for x in v:
                self.pack('>i', x)
        elif tid == TAG_LONG_ARRAY:
            self.pack('>i', len(v))
            for x in v:
                self.pack('>q', x)
        else:
            raise ValueError('tag inconnu: %d' % tid)


# ----- Nettoyage -----

# Blocs moddés dont l'équivalent vanilla est évident. Tout ce qui n'est pas ici et n'est pas
# vanilla fait échouer le script plutôt que de partir en air silencieusement.
BLOCK_SUBSTITUTIONS = {
    'visualworkbench:minecraft/crafting_table': 'minecraft:crafting_table',
}

# Blocs qui, une fois substitués, n'ont plus de BlockEntity : leur `nbt` doit disparaître.
NO_BLOCK_ENTITY = {'minecraft:crafting_table'}


def sanitize(root):
    changed = []

    if 'Railways_DataVersion' in root:
        del root['Railways_DataVersion']
        changed.append('Railways_DataVersion retiré')

    palette = root['palette'][1][1]
    for entry in palette:
        name = entry['Name'][1]
        if name in BLOCK_SUBSTITUTIONS:
            entry['Name'] = (TAG_STRING, BLOCK_SUBSTITUTIONS[name])
            changed.append('%s -> %s' % (name, BLOCK_SUBSTITUTIONS[name]))

    for block in root['blocks'][1][1]:
        if 'nbt' not in block:
            continue
        name = palette[block['state'][1]]['Name'][1]
        if name in NO_BLOCK_ENTITY:
            del block['nbt']
            changed.append('nbt de block entity retiré sur %s' % name)
            continue
        be = block['nbt'][1]
        if 'neoforge:attachments' in be:
            del be['neoforge:attachments']
            changed.append('neoforge:attachments retiré sur %s' % name)

    entities = root['entities'][1][1]
    if entities:
        changed.append('%d entité(s) retirée(s)' % len(entities))
        root['entities'] = (TAG_LIST, (TAG_COMPOUND, []))

    return changed


def verify(root):
    """Le mode d'échec réel est muet (un bloc inconnu devient de l'air sans un mot) : on
    l'attrape ici plutôt qu'en jeu."""
    palette = root['palette'][1][1]
    for entry in palette:
        name = entry['Name'][1]
        assert name.startswith('minecraft:'), 'bloc non vanilla dans la palette : %s' % name

    for block in root['blocks'][1][1]:
        if 'nbt' not in block:
            continue
        for key in block['nbt'][1]:
            assert ':' not in key or key.startswith('minecraft:'), \
                'clé de block entity non vanilla : %s' % key

    assert not root['entities'][1][1], 'des entités subsistent'
    assert 'DataVersion' in root, 'DataVersion absent : les correctifs de données saccageraient la palette'
    assert 'Railways_DataVersion' not in root, 'Railways_DataVersion subsiste'

    size = [v for v in root['size'][1][1]]
    return size, len(palette), len(root['blocks'][1][1])


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, os.pardir, 'src', 'main', 'resources', 'data',
                          'turnkey_factory', 'structure', 'starter_house.nbt')
    target = os.path.normpath(target)
    source = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else target

    with gzip.open(source, 'rb') as fh:
        data = fh.read()

    reader = Reader(data)
    tid = reader.payload(TAG_BYTE)
    reader.string()  # nom de la racine, vide
    assert tid == TAG_COMPOUND, 'racine NBT inattendue'
    root = reader.payload(TAG_COMPOUND)

    changed = sanitize(root)
    size, palette_size, block_count = verify(root)

    writer = Writer()
    writer.pack('>b', TAG_COMPOUND)
    writer.string('')
    writer.payload(TAG_COMPOUND, root)

    folder = os.path.dirname(target)
    if not os.path.isdir(folder):
        os.makedirs(folder)
    # mtime figé : sans ça gzip tamponne l'heure courante et le fichier ressort "modifié"
    # à chaque exécution, alors que son contenu est identique.
    with open(target, 'wb') as raw:
        with gzip.GzipFile(fileobj=raw, mode='wb', filename='', mtime=0) as fh:
            fh.write(bytes(writer.out))

    print('source  : %s' % source)
    print('cible   : %s' % target)
    print('taille  : %dx%dx%d, %d blocs, %d entrées de palette' %
          (size[0], size[1], size[2], block_count, palette_size))
    if changed:
        for line in changed:
            print('  - %s' % line)
    else:
        print('  (déjà propre)')


if __name__ == '__main__':
    main()
