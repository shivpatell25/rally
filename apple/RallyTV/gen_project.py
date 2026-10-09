#!/usr/bin/env python3
"""Generates apple/RallyTV/RallyTV.xcodeproj/project.pbxproj by scanning Swift sources.

Deterministic 24-hex UUIDs from path hashes, so regeneration is stable and
adding a file is just re-running this script. No gems, no tuist, no xcodegen.
"""
import hashlib
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))  # apple/RallyTV
SRC = os.path.join(ROOT, "RallyTV")
PROJDIR = os.path.join(ROOT, "RallyTV.xcodeproj")


def uid(key: str) -> str:
    return hashlib.md5(key.encode()).hexdigest()[:24].upper()


def collect() -> tuple[list[str], list[str]]:
    sources: list[str] = []
    resources: list[str] = []
    for dirpath, directories, filenames in os.walk(SRC):
        for name in list(directories):
            if name.endswith(".xcassets"):
                resources.append(os.path.relpath(os.path.join(dirpath, name), ROOT))
                directories.remove(name)
        for name in filenames:
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, ROOT)
            if name.endswith(".swift"):
                sources.append(rel)
            elif name.lower().endswith((".jpg", ".jpeg", ".png", ".ttf", ".ts", ".xcprivacy")):
                resources.append(rel)
    return sorted(sources), sorted(resources)


def main() -> None:
    sources, resources = collect()
    # UI tests compile into their own bundle — never into the app target.
    testSources = sorted(s for s in sources if "/UITests/" in s.replace(os.sep, "/"))
    unitSources = sorted(s for s in sources if "/Tests/" in s.replace(os.sep, "/"))
    unitResources = [r for r in resources if "/Tests/" in r.replace(os.sep, "/")]
    sources = sorted(s for s in sources if "/UITests/" not in s.replace(os.sep, "/") and "/Tests/" not in s.replace(os.sep, "/"))
    if not sources:
        sys.exit("no Swift sources found under " + SRC)

    # Fixed ids for structural objects.
    ids = {
        "project": uid("PBXProject:RallyTV"),
        "mainGroup": uid("PBXGroup:main"),
        "srcGroup": uid("PBXGroup:RallyTV"),
        "productsGroup": uid("PBXGroup:Products"),
        "appRef": uid("PBXFileReference:RallyTV.app"),
        "target": uid("PBXNativeTarget:RallyTV"),
        "sourcesPhase": uid("PBXSourcesBuildPhase:RallyTV"),
        "resourcesPhase": uid("PBXResourcesBuildPhase:RallyTV"),
        "frameworksPhase": uid("PBXFrameworksBuildPhase:RallyTV"),
        "testFrameworksPhase": uid("PBXFrameworksBuildPhase:RallyTVUITests"),
        "aetherPackage": uid("XCRemoteSwiftPackageReference:AetherEngine"),
        "aetherProduct": uid("XCSwiftPackageProductDependency:AetherEngine"),
        "aetherBuild": uid("PBXBuildFile:AetherEngine"),
        "testTarget": uid("PBXNativeTarget:RallyTVUITests"),
        "testProduct": uid("PBXFileReference:RallyTVUITests.xctest"),
        "testSourcesPhase": uid("PBXSourcesBuildPhase:RallyTVUITests"),
        "testDep": uid("PBXTargetDependency:RallyTVUITests->RallyTV"),
        "testProxy": uid("PBXContainerItemProxy:RallyTVUITests->RallyTV"),
        "testConfigList": uid("XCConfigurationList:RallyTVUITests"),
        "testDebug": uid("XCBuildConfiguration:RallyTVUITests:Debug"),
        "testRelease": uid("XCBuildConfiguration:RallyTVUITests:Release"),
        "projectConfigList": uid("XCConfigurationList:project"),
        "targetConfigList": uid("XCConfigurationList:target"),
        "projectDebug": uid("XCBuildConfiguration:project:Debug"),
        "projectRelease": uid("XCBuildConfiguration:project:Release"),
        "targetDebug": uid("XCBuildConfiguration:target:Debug"),
        "targetRelease": uid("XCBuildConfiguration:target:Release"),
    }

    # Folder subgroups keyed by full directory under RallyTV/ (e.g. IPTV/Stalker),
    # so Xcode resolves nested files to their real disk paths.
    folders: dict[str, list[str]] = {}
    for rel in sources:
        folder = os.path.dirname(rel)[len("RallyTV" + os.sep):]
        folders.setdefault(folder, []).append(rel)
    for rel in resources:
        folder = os.path.dirname(rel)[len("RallyTV" + os.sep):]
        folders.setdefault(folder, []).append(rel)
    for rel in testSources + unitSources:
        folder = os.path.dirname(rel)[len("RallyTV" + os.sep):]
        folders.setdefault(folder, []).append(rel)
    group_ids = {f: uid("PBXGroup:" + f) for f in folders}
    file_ids = {rel: uid("PBXFileReference:" + rel) for rel in sources}
    build_ids = {rel: uid("PBXBuildFile:" + rel) for rel in sources}
    res_file_ids = {rel: uid("PBXFileReference:" + rel) for rel in resources}
    res_build_ids = {rel: uid("PBXBuildFile:" + rel) for rel in resources}
    test_file_ids = {rel: uid("PBXFileReference:" + rel) for rel in testSources + unitSources}
    test_build_ids = {rel: uid("PBXBuildFile:" + rel) for rel in testSources + unitSources}
    all_file_ids = {**file_ids, **res_file_ids, **test_file_ids}
    L: list[str] = []

    def section(name: str) -> None:
        L.append(f"/* Begin {name} section */")

    def end_section(name: str) -> None:
        L.append(f"/* End {name} section */")
    # PBXBuildFile
    section("PBXBuildFile")
    L.append(f"\t\t{ids['aetherBuild']} = {{isa = PBXBuildFile; productRef = {ids['aetherProduct']}; }};")
    for rel in sources:
        L.append(f"\t\t{build_ids[rel]} = {{isa = PBXBuildFile; fileRef = {file_ids[rel]}; }};")
    for rel in resources:
        L.append(f"\t\t{res_build_ids[rel]} = {{isa = PBXBuildFile; fileRef = {res_file_ids[rel]}; }};")
    for rel in testSources + unitSources:
        L.append(f"\t\t{test_build_ids[rel]} = {{isa = PBXBuildFile; fileRef = {test_file_ids[rel]}; }};")
    end_section("PBXBuildFile")

    # PBXFileReference
    section("PBXFileReference")
    L.append(f"\t\t{ids['appRef']} = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = RallyTV.app; sourceTree = BUILT_PRODUCTS_DIR; }};")
    L.append(f"\t\t{ids['testProduct']} = {{isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = RallyTVUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR; }};")
    for rel in sources:
        name = os.path.basename(rel)
        L.append(f"\t\t{file_ids[rel]} = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; name = {name}; path = {name}; sourceTree = \"<group>\"; }};")
    for rel in resources:
        name = os.path.basename(rel)
        ext = os.path.splitext(name)[1].lower()
        ftype = "folder.assetcatalog" if ext == ".xcassets" else "file" if ext in (".ttf", ".ts", ".xcprivacy") else ("image.png" if ext == ".png" else "image.jpeg")
        L.append(f"\t\t{res_file_ids[rel]} = {{isa = PBXFileReference; lastKnownFileType = {ftype}; name = \"{name}\"; path = \"{name}\"; sourceTree = \"<group>\"; }};")
    for rel in testSources + unitSources:
        name = os.path.basename(rel)
        L.append(f"\t\t{test_file_ids[rel]} = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; name = {name}; path = {name}; sourceTree = \"<group>\"; }};")
    end_section("PBXFileReference")

    # PBXFrameworksBuildPhase
    section("PBXFrameworksBuildPhase")
    L.append(f"\t\t{ids['frameworksPhase']} = {{isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = ({ids['aetherBuild']}); runOnlyForDeploymentPostprocessing = 0; }};")
    L.append(f"\t\t{ids['testFrameworksPhase']} = {{isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; }};")
    end_section("PBXFrameworksBuildPhase")

    # PBXGroup
    section("PBXGroup")
    L.append(f"\t\t{ids['mainGroup']} = {{isa = PBXGroup; children = ({ids['srcGroup']}, {ids['productsGroup']}); sourceTree = \"<group>\"; }};")
    L.append(f"\t\t{ids['productsGroup']} = {{isa = PBXGroup; children = ({ids['appRef']}, {ids['testProduct']}, {uid('unitProduct')}); name = Products; sourceTree = \"<group>\"; }};")
    subgroup_list = ", ".join(group_ids[f] for f in sorted(group_ids))
    L.append(f"\t\t{ids['srcGroup']} = {{isa = PBXGroup; children = ({subgroup_list}); path = RallyTV; sourceTree = \"<group>\"; }};")
    for folder in sorted(group_ids):
        members = ", ".join(all_file_ids[rel] for rel in folders[folder])
        display = folder.split(os.sep)[-1]
        L.append(f"\t\t{group_ids[folder]} = {{isa = PBXGroup; children = ({members}); name = {display}; path = {folder}; sourceTree = \"<group>\"; }};")
    end_section("PBXGroup")

    # PBXNativeTarget
    section("PBXNativeTarget")
    L.append(
        f"\t\t{ids['target']} = {{isa = PBXNativeTarget; "
        f"buildConfigurationList = {ids['targetConfigList']}; "
        f"buildPhases = ({ids['sourcesPhase']}, {ids['frameworksPhase']}, {ids['resourcesPhase']}); "
        f"buildRules = (); dependencies = (); name = RallyTV; "
        f"productName = RallyTV; productReference = {ids['appRef']}; "
        f"packageProductDependencies = ({ids['aetherProduct']}); productType = \"com.apple.product-type.application\"; }};"
    )
    L.append(
        f"\t\t{ids['testTarget']} = {{isa = PBXNativeTarget; "
        f"buildConfigurationList = {ids['testConfigList']}; "
        f"buildPhases = ({ids['testSourcesPhase']}, {ids['testFrameworksPhase']}); "
        f"buildRules = (); dependencies = ({ids['testDep']}); name = RallyTVUITests; "
        f"productName = RallyTVUITests; productReference = {ids['testProduct']}; "
        f"productType = \"com.apple.product-type.bundle.ui-testing\"; }};"
    )
    end_section("PBXNativeTarget")

    # PBXTargetDependency + proxy back to the app target
    section("PBXTargetDependency")
    L.append(
        f"\t\t{ids['testDep']} = {{isa = PBXTargetDependency; "
        f"target = {ids['target']}; targetProxy = {ids['testProxy']}; }};"
    )
    end_section("PBXTargetDependency")

    # PBXContainerItemProxy
    section("PBXContainerItemProxy")
    L.append(
        f"\t\t{ids['testProxy']} = {{isa = PBXContainerItemProxy; "
        f"containerPortal = {ids['project']}; proxyType = 1; "
        f"remoteGlobalIDString = {ids['target']}; remoteInfo = RallyTV; }};"
    )
    end_section("PBXContainerItemProxy")

    # PBXProject
    section("PBXProject")
    L.append(
        f"\t\t{ids['project']} = {{isa = PBXProject; "
        f"attributes = {{LastUpgradeCheck = 2700; TargetAttributes = {{{ids['target']} = {{CreatedOnToolsVersion = 27.0; }}; {ids['testTarget']} = {{CreatedOnToolsVersion = 27.0; }}; }}; }}; "
        f"buildConfigurationList = {ids['projectConfigList']}; "
        f"compatibilityVersion = \"Xcode 16.0\"; developmentRegion = en; "
        f"hasScannedForEncodings = 0; knownRegions = (en, Base); "
        f"mainGroup = {ids['mainGroup']}; productRefGroup = {ids['productsGroup']}; "
        f"packageReferences = ({ids['aetherPackage']}); "
        f"projectDirPath = \"\"; projectRoot = \"\"; targets = ({ids['target']}, {ids['testTarget']}, {uid("unitTarget")}); }};"
    )
    end_section("PBXProject")

    # PBXSourcesBuildPhase
    section("PBXSourcesBuildPhase")
    files = ", ".join(build_ids[rel] for rel in sources)
    L.append(f"\t\t{ids['sourcesPhase']} = {{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({files}); runOnlyForDeploymentPostprocessing = 0; }};")
    end_section("PBXSourcesBuildPhase")

    # PBXResourcesBuildPhase
    section("PBXResourcesBuildPhase")
    res_files = ", ".join(res_build_ids[rel] for rel in resources if rel not in unitResources)
    L.append(f"\t\t{ids['resourcesPhase']} = {{isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({res_files}); runOnlyForDeploymentPostprocessing = 0; }};")
    end_section("PBXResourcesBuildPhase")

    # Test sources
    section("PBXSourcesBuildPhase")
    test_files = ", ".join(test_build_ids[rel] for rel in testSources)
    L.append(f"\t\t{ids['testSourcesPhase']} = {{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({test_files}); runOnlyForDeploymentPostprocessing = 0; }};")
    end_section("PBXSourcesBuildPhase")

    section("XCRemoteSwiftPackageReference")
    L.append(f"\t\t{ids['aetherPackage']} = {{isa = XCRemoteSwiftPackageReference; repositoryURL = \"https://github.com/superuser404notfound/AetherEngine.git\"; requirement = {{kind = exactVersion; version = 6.89.1; }}; }};")
    end_section("XCRemoteSwiftPackageReference")
    section("XCSwiftPackageProductDependency")
    L.append(f"\t\t{ids['aetherProduct']} = {{isa = XCSwiftPackageProductDependency; package = {ids['aetherPackage']}; productName = AetherEngine; }};")
    end_section("XCSwiftPackageProductDependency")

    # XCBuildConfiguration
    section("XCBuildConfiguration")
    L.append(
        f"\t\t{ids['projectDebug']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{ALWAYS_SEARCH_USER_PATHS = NO; CLANG_ANALYZER_NONNULL = YES; CLANG_CXX_LANGUAGE_STANDARD = \"gnu++20\"; "
        f"GCC_C_LANGUAGE_STANDARD = gnu17; MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE; MTL_FAST_MATH = YES; "
        f"ONLY_ACTIVE_ARCH = YES; SDKROOT = appletvos; }}; name = Debug; }};"
    )
    L.append(
        f"\t\t{ids['projectRelease']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{ALWAYS_SEARCH_USER_PATHS = NO; CLANG_ANALYZER_NONNULL = YES; CLANG_CXX_LANGUAGE_STANDARD = \"gnu++20\"; "
        f"GCC_C_LANGUAGE_STANDARD = gnu17; MTL_FAST_MATH = YES; SDKROOT = appletvos; }}; name = Release; }};"
    )
    target_common = (
        "ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES; "
        "ASSETCATALOG_COMPILER_APPICON_NAME = Rally; "
        "CODE_SIGN_STYLE = Automatic; "
        "COPY_PHASE_STRIP = NO; "
        "CURRENT_PROJECT_VERSION = 17; "
        "ENABLE_PREVIEWS = YES; "
        "GENERATE_INFOPLIST_FILE = YES; "
        "INFOPLIST_KEY_CFBundleDisplayName = Rally; INFOPLIST_FILE = RallyTV/App/Info.plist; "
        "LD_RUNPATH_SEARCH_PATHS = (\"@executable_path/Frameworks\"); "
        "MARKETING_VERSION = 1.0; "
        "PRODUCT_BUNDLE_IDENTIFIER = com.shiv.rally.tv; "
        "PRODUCT_NAME = \"$(TARGET_NAME)\"; "
        "SUPPORTED_PLATFORMS = \"appletvos appletvsimulator\"; "
        "SUPPORTS_MACCATALYST = NO; "
        "SWIFT_VERSION = 5.0; "
        "TARGETED_DEVICE_FAMILY = 3; "
        "TVOS_DEPLOYMENT_TARGET = 17.0;"
    )
    L.append(
        f"\t\t{ids['targetDebug']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{DEBUG_INFORMATION_FORMAT = dwarf; ENABLE_TESTABILITY = YES; GCC_OPTIMIZATION_LEVEL = 0; "
        f"SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG; MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE; ONLY_ACTIVE_ARCH = YES; SWIFT_OPTIMIZATION_LEVEL = \"-Onone\"; "
        f"{target_common}}}; name = Debug; }};"
    )
    L.append(
        f"\t\t{ids['targetRelease']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{COPY_PHASE_STRIP = YES; DEBUG_INFORMATION_FORMAT = \"dwarf-with-dsym\"; "
        f"MTL_ENABLE_DEBUG_INFO = NO; SWIFT_OPTIMIZATION_LEVEL = \"-O\"; "
        f"{target_common}}}; name = Release; }};"
    )
    test_common = (
        "CODE_SIGN_STYLE = Automatic; "
        "COPY_PHASE_STRIP = NO; "
        "CURRENT_PROJECT_VERSION = 1; "
        "GENERATE_INFOPLIST_FILE = YES; "
        "LD_RUNPATH_SEARCH_PATHS = (\"@executable_path/Frameworks\", \"@loader_path/Frameworks\"); "
        "MARKETING_VERSION = 1.0; "
        "PRODUCT_BUNDLE_IDENTIFIER = com.shiv.rally.tv.uitests; "
        "PRODUCT_NAME = \"$(TARGET_NAME)\"; "
        "SUPPORTED_PLATFORMS = \"appletvos appletvsimulator\"; "
        "SWIFT_VERSION = 5.0; "
        "TARGETED_DEVICE_FAMILY = 3; "
        "TEST_TARGET_NAME = RallyTV; "
        "TVOS_DEPLOYMENT_TARGET = 17.0;"
    )
    L.append(
        f"\t\t{ids['testDebug']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{DEBUG_INFORMATION_FORMAT = dwarf; ENABLE_TESTABILITY = YES; GCC_OPTIMIZATION_LEVEL = 0; "
        f"SWIFT_OPTIMIZATION_LEVEL = \"-Onone\"; ONLY_ACTIVE_ARCH = YES; "
        f"{test_common}}}; name = Debug; }};"
    )
    L.append(
        f"\t\t{ids['testRelease']} = {{isa = XCBuildConfiguration; buildSettings = "
        f"{{DEBUG_INFORMATION_FORMAT = dwarf-with-dsym; SWIFT_OPTIMIZATION_LEVEL = \"-O\"; "
        f"{test_common}}}; name = Release; }};"
    )
    end_section("XCBuildConfiguration")

    # XCConfigurationList
    section("XCConfigurationList")
    L.append(
        f"\t\t{ids['projectConfigList']} = {{isa = XCConfigurationList; "
        f"buildConfigurations = ({ids['projectDebug']}, {ids['projectRelease']}); "
        f"defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};"
    )
    L.append(
        f"\t\t{ids['targetConfigList']} = {{isa = XCConfigurationList; "
        f"buildConfigurations = ({ids['targetDebug']}, {ids['targetRelease']}); "
        f"defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};"
    )
    L.append(
        f"\t\t{ids['testConfigList']} = {{isa = XCConfigurationList; "
        f"buildConfigurations = ({ids['testDebug']}, {ids['testRelease']}); "
        f"defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};"
    )
    end_section("XCConfigurationList")
    # Hosted unit tests exercise the actual shipping models, parser and layout math.
    u = {key: uid(key) for key in ["unitTarget", "unitProduct", "unitSources", "unitResources", "unitFrameworks", "unitDependency", "unitProxy", "unitConfig", "unitDebug", "unitRelease"]}
    L += [
        f"{u['unitProduct']} = {{isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = RallyTVTests.xctest; sourceTree = BUILT_PRODUCTS_DIR; }};",
        f"{u['unitTarget']} = {{isa = PBXNativeTarget; buildConfigurationList = {u['unitConfig']}; buildPhases = ({u['unitSources']}, {u['unitFrameworks']}, {u['unitResources']}); buildRules = (); dependencies = ({u['unitDependency']}); name = RallyTVTests; productName = RallyTVTests; productReference = {u['unitProduct']}; productType = \"com.apple.product-type.bundle.unit-test\"; }};",
        f"{u['unitResources']} = {{isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({', '.join(res_build_ids[p] for p in unitResources)}); runOnlyForDeploymentPostprocessing = 0; }};",
        f"{u['unitSources']} = {{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({', '.join(test_build_ids[p] for p in unitSources)}); runOnlyForDeploymentPostprocessing = 0; }};",
        f"{u['unitFrameworks']} = {{isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; }};",
        f"{u['unitDependency']} = {{isa = PBXTargetDependency; target = {ids['target']}; targetProxy = {u['unitProxy']}; }};",
        f"{u['unitProxy']} = {{isa = PBXContainerItemProxy; containerPortal = {ids['project']}; proxyType = 1; remoteGlobalIDString = {ids['target']}; remoteInfo = RallyTV; }};",
        f"{u['unitConfig']} = {{isa = XCConfigurationList; buildConfigurations = ({u['unitDebug']}, {u['unitRelease']}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};"
    ]
    for configuration in ["Debug", "Release"]:
        L.append(f"{u['unit' + configuration]} = {{isa = XCBuildConfiguration; buildSettings = {{GENERATE_INFOPLIST_FILE = YES; PRODUCT_NAME = \"$(TARGET_NAME)\"; PRODUCT_BUNDLE_IDENTIFIER = com.shiv.rally.tv.tests; SWIFT_VERSION = 5.0; SWIFT_OPTIMIZATION_LEVEL = \"-Onone\"; TARGETED_DEVICE_FAMILY = 3; TVOS_DEPLOYMENT_TARGET = 17.0; TEST_HOST = \"$(BUILT_PRODUCTS_DIR)/RallyTV.app/RallyTV\"; BUNDLE_LOADER = \"$(TEST_HOST)\"; LD_RUNPATH_SEARCH_PATHS = (\"@executable_path/Frameworks\", \"@loader_path/Frameworks\"); }}; name = {configuration}; }};")
    body = "\n".join(L)
    pbxproj = (
        "// !$*UTF8*$!\n"
        "{\n"
        "\tarchiveVersion = 1;\n"
        "\tclasses = {\n"
        "\t};\n"
        "\tobjectVersion = 77;\n"
        "\tobjects = {\n"
        f"{body}\n"
        "\t};\n"
        f"\trootObject = {ids['project']};\n"
        "}\n"
    )
    print(f"wrote project.pbxproj with {len(sources)} sources, {len(testSources)} tests, {len(resources)} resources")
    os.makedirs(PROJDIR, exist_ok=True)
    with open(os.path.join(PROJDIR, "project.pbxproj"), "w") as f:
        f.write(pbxproj)


if __name__ == "__main__":
    main()
