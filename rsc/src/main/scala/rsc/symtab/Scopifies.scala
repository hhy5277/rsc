// Copyright (c) 2017-2019 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (see LICENSE.md).
package rsc.symtab

import rsc.input._
import rsc.outline._
import rsc.semantics._
import rsc.syntax._
import rsc.util._
import scala.meta.internal.{semanticdb => s}

trait Scopifies {
  self: Symtab =>

  def scopify(sym: Symbol): ScopeResolution = {
    if (scopes.contains(sym)) {
      ResolvedScope(scopes(sym))
    } else {
      metadata(sym) match {
        case OutlineMetadata(outline) =>
          outline match {
            case DefnMethod(mods, _, _, _, Some(tpt), _) if mods.hasVal => scopify(tpt)
            case outline: DefnType => scopify(outline.desugaredUbound)
            case outline: TypeParam => scopify(outline.desugaredUbound)
            case Param(_, _, Some(tpt), _) => scopify(tpt)
            case outline: Self => scopify(desugars.rets(outline))
            case _ => crash(outline)
          }
        case ClasspathMetadata(info) =>
          info.signature match {
            case sig: s.MethodSignature if info.isVal => scopify(sig.returnType)
            case sig: s.TypeSignature => scopify(sig.upperBound)
            case sig: s.ValueSignature => scopify(sig.tpe)
            case sig => crash(info.toProtoString)
          }
        case NoMetadata =>
          MissingResolution
      }
    }
  }

  def scopify(tpt: Tpt): ScopeResolution = {
    tpt match {
      case TptAnnotate(tpt, _) =>
        scopify(tpt)
      case TptArray(_) =>
        scopify(ArrayClass)
      case TptByName(tpt) =>
        scopify(tpt)
      case TptApply(fun, _) =>
        scopify(fun)
      case TptExistential(tpt, _) =>
        scopify(tpt)
      case TptIntersect(_) =>
        crash(tpt)
      case TptLit(_) =>
        crash(tpt)
      case tpt: TptPath =>
        tpt.id.sym match {
          case NoSymbol => BlockedResolution(Unknown())
          case sym => scopify(sym)
        }
      case tpt: TptPrimitive =>
        crash(tpt)
      case tpt: TptRefine =>
        crash(tpt)
      case TptRepeat(tpt) =>
        scopify(SeqClass)
      case tpt: TptWildcard =>
        scopify(tpt.desugaredUbound)
      case TptWildcardExistential(_, tpt) =>
        scopify(tpt)
      case TptWith(tpts) =>
        val buf = List.newBuilder[Scope]
        tpts.foreach { tpt =>
          scopify(tpt) match {
            case ResolvedScope(scope) => buf += scope
            case other => return other
          }
        }
        val scope = WithScope(buf.result)
        scope.succeed()
        ResolvedScope(scope)
    }
  }

  def scopify(tpe: s.Type): ScopeResolution = {
    tpe match {
      case s.TypeRef(_, sym, _) =>
        scopify(sym)
      case s.SingleType(_, sym) =>
        scopify(sym)
      case s.StructuralType(tpe, Some(decls)) if decls.symbols.isEmpty =>
        scopify(tpe)
      case s.WithType(tpes) =>
        val buf = List.newBuilder[Scope]
        tpes.foreach { tpe =>
          scopify(tpe) match {
            case ResolvedScope(scope) => buf += scope
            case other => return other
          }
        }
        val scope = WithScope(buf.result)
        scope.succeed()
        ResolvedScope(scope)
      case _ =>
        crash(tpe.asMessage.toProtoString)
    }
  }

  private implicit class BoundedScopifyOps(bounded: Bounded) {
    def desugaredUbound: Tpt = {
      bounded.lang match {
        case ScalaLanguage | UnknownLanguage =>
          bounded.ubound.getOrElse(TptId("Any").withSym(AnyClass))
        case JavaLanguage =>
          bounded.ubound.getOrElse(TptId("Object").withSym(ObjectClass))
      }
    }
  }
}
