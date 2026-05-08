package com.example.bidmart.user.controller;

import com.example.bidmart.user.model.Permission;
import com.example.bidmart.user.model.Role;
import com.example.bidmart.user.repository.PermissionRepository;
import com.example.bidmart.user.repository.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @PostMapping("/{roleName}/permissions/{permissionName}")
    @Transactional
    public ResponseEntity<String> assignPermission(
            @PathVariable String roleName, 
            @PathVariable String permissionName) {
        
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role tidak ditemukan"));
        Permission permission = permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new IllegalArgumentException("Permission tidak ditemukan"));

        role.getPermissions().add(permission);
        roleRepository.save(role);

        return ResponseEntity.ok("Permission '" + permissionName + "' berhasil ditambahkan ke role '" + roleName + "'");
    }

    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody String roleName) {
        if (roleRepository.findByName(roleName).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        Role newRole = new Role();
        newRole.setName(roleName);
        return ResponseEntity.ok(roleRepository.save(newRole));
    }
}