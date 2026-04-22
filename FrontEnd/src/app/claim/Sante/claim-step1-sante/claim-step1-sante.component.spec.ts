import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep1SanteComponent } from './claim-step1-sante.component';

describe('ClaimStep1SanteComponent', () => {
  let component: ClaimStep1SanteComponent;
  let fixture: ComponentFixture<ClaimStep1SanteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep1SanteComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep1SanteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
