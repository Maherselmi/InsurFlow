import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DossierSinistreComponent } from './dossier-sinistre.component';

describe('DossierSinistreComponent', () => {
  let component: DossierSinistreComponent;
  let fixture: ComponentFixture<DossierSinistreComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DossierSinistreComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DossierSinistreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
